package com.example.vijayraghavan.monthlynewmovies;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Configuration;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;


import java.util.List;


import Adapter.movieAdapter;

import Retrofit.ApiClient;
import Retrofit.ApiInterface;
import TVv2API_Model.Movies;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import service.ScheduleMoviesDownload;

public class MainActivity extends AppCompatActivity {

    public static int page = 1;


    private RecyclerView recyclerView;
    private movieAdapter adapter;
    private ProgressBar progressBar;
    private Toolbar toolbar;



    private static final String TAG = MainActivity.class.getName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
//        RecentMovies recentMovies = new RecentMovies();
//        recentMovies.getRecentMovies();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        toolbar = findViewById(R.id.toolbar_main_activity);
        setSupportActionBar(toolbar);


        progressBar = findViewById(R.id.progressBar);
        progressBar.setVisibility(View.VISIBLE);


        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setHasFixedSize(true);
//        recyclerView.setItemViewCacheSize(20);
//        recyclerView.setDrawingCacheEnabled(true);
        recyclerView.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);
        ApiInterface apiService = ApiClient.getRetrofit().create(ApiInterface.class);

/*        Call<MoviesResponse> moviesResponseCall = apiService.getListOfMovies(50);
        moviesResponseCall.enqueue(new Callback<MoviesResponse>() {
            @Override
            public void onResponse(Call<MoviesResponse> call, Response<MoviesResponse> response) {
                Log.e("sasi", "before");
                MoviesResponse.data data = response.body().getData();

                Log.e("sasi", "" +response.body().getStatus());

                adapter = new movieAdapter(data.getMovies(), recyclerView, getApplicationContext());
                progressBar.setVisibility(View.INVISIBLE);
                recyclerView.setAdapter(adapter);
                if(getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT)
                    recyclerView.setLayoutManager(new GridLayoutManager(getApplicationContext(), 2));
                else
                    recyclerView.setLayoutManager(new GridLayoutManager(getApplicationContext(), 3));


            }

            @Override
            public void onFailure(Call<MoviesResponse> call, Throwable t) {
                Log.e(TAG, t.getMessage());
            }
        });*/


        Call<List<Movies>> moviesCall = apiService.getAllMovies(page++, "trending");
        moviesCall.enqueue(new Callback<List<Movies>>() {
            @Override
            public void onResponse(Call<List<Movies>> call, Response<List<Movies>> response) {
//                Log.e(TAG, "onResponse: " + response.body().get(0).getTitle());
                List<Movies> movies = response.body();
                adapter = new movieAdapter(movies, recyclerView, getApplicationContext());
                progressBar.setVisibility(View.INVISIBLE);
                recyclerView.setAdapter(adapter);
                if(getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT)
                    recyclerView.setLayoutManager(new GridLayoutManager(getApplicationContext(), 2));
                else
                    recyclerView.setLayoutManager(new GridLayoutManager(getApplicationContext(), 3));





            }

            @Override
            public void onFailure(Call<List<Movies>> call, Throwable t) {
                Log.e(TAG, "onFailure: " + t.getMessage());
            }
        });



        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if(dy > 0){ // only when scrolling up




                    final int visibleThreshold = 2;

                    GridLayoutManager layoutManager = (GridLayoutManager) recyclerView.getLayoutManager();
                    int lastItem  = layoutManager.findLastCompletelyVisibleItemPosition();
                    int currentTotalCount = layoutManager.getItemCount();

                    if(currentTotalCount <= lastItem + visibleThreshold){
                        //show your loading view
                        // load content in background


                        final movieAdapter movieAdapter = (Adapter.movieAdapter) recyclerView.getAdapter();
                        ApiInterface apiService = ApiClient.getRetrofit().create(ApiInterface.class);
                        Call<List<Movies>> moviesCall = apiService.getAllMovies(page++,"trending");
                        moviesCall.enqueue(new Callback<List<Movies>>() {
                            @Override
                            public void onResponse(Call<List<Movies>> call, Response<List<Movies>> response) {
                                Log.e(TAG, "onResponse: called" );
                                movieAdapter.addMoreMovies(response.body());


                            }

                            @Override
                            public void onFailure(Call<List<Movies>> call, Throwable t) {
                                Log.e(TAG, "onFailure: "+ t.getMessage());
                            }
                        });


                    }
                }


                }


        });

    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
//        MenuInflater menuInflater = getMenuInflater();
        toolbar.inflateMenu(R.menu.activity_main_menu);

        toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                return onOptionsItemSelected(menuItem);
            }
        });

        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        JobScheduler jobScheduler = (JobScheduler) getApplicationContext().getSystemService(JOB_SCHEDULER_SERVICE);

        switch (item.getItemId()){
            case R.id.setting:
                Intent intent = new Intent(this, SettingActivity.class);
                startActivity(intent);
                break;

            case R.id.activity_main_menu:
                Toast.makeText(getApplicationContext(), "clicked", Toast.LENGTH_LONG).show();

                ComponentName componentName = new ComponentName(getApplicationContext(), ScheduleMoviesDownload.class);
                JobInfo jobInfo = new JobInfo.Builder(8456, componentName)
                        .setMinimumLatency(20 * 1000).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .build();

                jobScheduler.schedule(jobInfo);
                break;


            case R.id.cancel_schedule:
                jobScheduler.cancel(8456);
                break;

            default:
                 return super.onOptionsItemSelected(item);
        }
        return true;

    }
}
