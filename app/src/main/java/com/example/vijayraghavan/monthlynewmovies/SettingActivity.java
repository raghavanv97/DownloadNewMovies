package com.example.vijayraghavan.monthlynewmovies;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

import service.ScheduleMoviesDownload;

import static Constants.Utils.IMDB_RATING;
import static Constants.Utils.MONTHLY_SCHEDULE_TIMING;
import static Constants.Utils.SCHEDULE_DAILY;
import static Constants.Utils.SCHEDULE_MONTHLY;
import static Constants.Utils.SCHEDULE_ONE_MINUTE;
import static Constants.Utils.SCHEDULE_WEEKLY;
import static Constants.Utils.SHARED_PREFERENCE;

public class SettingActivity extends AppCompatActivity {

    private Spinner mSpinner;
    private ArrayAdapter<String> mSpinnerAdapter;
    JobScheduler jobScheduler;
    SharedPreferences preferences;
    private Spinner imdb_rating_spinner;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);

        Toolbar toolbar = findViewById(R.id.toolbar_settings);
        toolbar.setTitle("Settings");
        setSupportActionBar(toolbar);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        mSpinner = findViewById(R.id.setting_spinner);
        mSpinnerAdapter = new ArrayAdapter<>(this, R.layout.custom_spinner_layout, getResources().getStringArray(R.array.schedule_movie));
        mSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        jobScheduler = (JobScheduler) getApplicationContext().getSystemService(JOB_SCHEDULER_SERVICE);

        mSpinner.setAdapter(mSpinnerAdapter);

        imdb_rating_spinner = findViewById(R.id.spinner_imdb);
        ArrayAdapter<String> imdb_spinner_adapter = new ArrayAdapter<>(this, R.layout.custom_spinner_layout, getResources().getStringArray(R.array.imdb_rating));
        imdb_spinner_adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        imdb_rating_spinner.setAdapter(imdb_spinner_adapter);

        preferences = getSharedPreferences(SHARED_PREFERENCE, MODE_PRIVATE);

        setDropDownSpinner();

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

                jobScheduler.cancel(8456);

                Editor editor = preferences.edit();

                long time;
                boolean monthorday = false;
                Calendar today = Calendar.getInstance();
                Calendar calculate = Calendar.getInstance();
                switch (mSpinner.getSelectedItemPosition()){
                    case 0:
                        calculate.add(Calendar.DATE, 30);
                        editor.putString(MONTHLY_SCHEDULE_TIMING, SCHEDULE_MONTHLY).apply();
                        break;
                    case 1:
                        calculate.add(Calendar.DATE, 7);
                        editor.putString(MONTHLY_SCHEDULE_TIMING, SCHEDULE_WEEKLY).apply();
                        break;
                    case 2:
                        calculate.add(Calendar.DATE, 1);
                        editor.putString(MONTHLY_SCHEDULE_TIMING, SCHEDULE_DAILY).apply();
                        break;
                    case 3:
                        calculate.add(Calendar.MINUTE, 1);
                        editor.putString(MONTHLY_SCHEDULE_TIMING, SCHEDULE_ONE_MINUTE).apply();
                        monthorday = true;
                        break;

                }
                time = calculate.getTimeInMillis() - today.getTimeInMillis();
                Toast.makeText(SettingActivity.this, "Scheduling Movies Download " + (monthorday?TimeUnit.MILLISECONDS.toMinutes(time) + " minute" : TimeUnit.MILLISECONDS.toDays(time) + " days") , Toast.LENGTH_SHORT).show();



                switch (imdb_rating_spinner.getSelectedItemPosition()){
                    case 0:
                        editor.putString(IMDB_RATING, "70").apply();
                        break;

                    case 1:
                        editor.putString(IMDB_RATING, "75").apply();
                        break;

                    case 2:
                        editor.putString(IMDB_RATING, "80").apply();
                        break;

                    case 3:
                        editor.putString(IMDB_RATING, "85").apply();
                        break;

                    case 4:
                        editor.putString(IMDB_RATING, "90").apply();
                        break;

                    case 5:
                        editor.putString(IMDB_RATING, "95").apply();
                        break;


                }

                ComponentName componentName = new ComponentName(getApplicationContext(), ScheduleMoviesDownload.class);
                JobInfo jobInfo = new JobInfo.Builder(8456, componentName)
                        .setMinimumLatency(time).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .build();

                jobScheduler.schedule(jobInfo);
            }
        });
    }

    private void setDropDownSpinner(){
        String imdbPosition = preferences.getString(IMDB_RATING, "75");

        switch (imdbPosition){
            case "70":
                imdb_rating_spinner.setSelection(0);
                break;
            case "75":
                imdb_rating_spinner.setSelection(1);
                break;
            case "80":
                imdb_rating_spinner.setSelection(2);
                break;
            case "85":
                imdb_rating_spinner.setSelection(3);
                break;
            case "90":
                imdb_rating_spinner.setSelection(4);
                break;
        }

        String schedule_timing = preferences.getString(MONTHLY_SCHEDULE_TIMING, SCHEDULE_DAILY);

        switch (schedule_timing){
            case SCHEDULE_MONTHLY:
                mSpinner.setSelection(0);
                break;

            case SCHEDULE_WEEKLY:
                mSpinner.setSelection(1);
                break;

            case SCHEDULE_DAILY:
                mSpinner.setSelection(2);
                break;

            case SCHEDULE_ONE_MINUTE:
                mSpinner.setSelection(3);
                break;
        }

    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
