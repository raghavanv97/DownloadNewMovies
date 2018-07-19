package service;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import java.util.List;

import TVv2API_Model.RecentMovies;
//
//import Model.Movie;
//import Model.RecentMovies;

public class ScheduleMoviesDownload extends JobService {



    @Override
    public boolean onStartJob(final JobParameters jobParameters) {

        Log.e("In onstartJob", "processing");



//                    RecentMovies recentMovies = new RecentMovies();
//
//                    recentMovies.getRecentMovies(getApplicationContext());
//                    Log.e("sasi", "run: after calling getrecentMovies method");
//
//
//                    jobFinished(jobParameters, true);
        RecentMovies recentMovies = new RecentMovies();
        recentMovies.getRecentMovies(getApplicationContext());
        jobFinished(jobParameters, true);


        Log.e("In onstartJob", "finished");

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        return false;
    }
}
