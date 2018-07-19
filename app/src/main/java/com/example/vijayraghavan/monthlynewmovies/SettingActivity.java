package com.example.vijayraghavan.monthlynewmovies;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

import service.ScheduleMoviesDownload;

public class SettingActivity extends AppCompatActivity {

    private Spinner mSpinner;
    private ArrayAdapter<String> mSpinnerAdapter;
    JobScheduler jobScheduler;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);
        mSpinner = findViewById(R.id.setting_spinner);
        mSpinnerAdapter = new ArrayAdapter<>(this, R.layout.custom_spinner_layout, getResources().getStringArray(R.array.schedule_movie));
        mSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        jobScheduler = (JobScheduler) getApplicationContext().getSystemService(JOB_SCHEDULER_SERVICE);

        mSpinner.setAdapter(mSpinnerAdapter);

        findViewById(R.id.cancelButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        Button cancelSchedule = findViewById(R.id.cancel_sch);
        cancelSchedule.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(SettingActivity.this, "Cancelled Schedule", Toast.LENGTH_SHORT).show();
                jobScheduler.cancel(8456);

            }
        });


        Button okay = findViewById(R.id.Okay);
        okay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                long time;
                boolean monthorday = false;
                Calendar today = Calendar.getInstance();
                Calendar calculate = Calendar.getInstance();
                switch (mSpinner.getSelectedItemPosition()){
                    case 0:
                        calculate.add(Calendar.DATE, 30);
                        break;
                    case 1:
                        calculate.add(Calendar.DATE, 7);
                        break;
                    case 2:
                        calculate.add(Calendar.DATE, 1);
                        break;
                    case 3:
                        calculate.add(Calendar.MINUTE, 1);
                        monthorday = true;
                        break;

                }
                time = calculate.getTimeInMillis() - today.getTimeInMillis();
                Toast.makeText(SettingActivity.this, "Scheduling Movies Download " + (monthorday?TimeUnit.MILLISECONDS.toMinutes(time) + " minute" : TimeUnit.MILLISECONDS.toDays(time) + " days") , Toast.LENGTH_SHORT).show();


                ComponentName componentName = new ComponentName(getApplicationContext(), ScheduleMoviesDownload.class);
                JobInfo jobInfo = new JobInfo.Builder(8456, componentName)
                        .setMinimumLatency(time).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .build();

                jobScheduler.schedule(jobInfo);
            }
        });
    }
}
