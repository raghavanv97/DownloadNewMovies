package TVv2API_Model;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import java.util.Calendar;
import java.util.List;

import Retrofit.ApiClient;
import Retrofit.ApiInterface;
import Torrent.AddTorrentActivity;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static Constants.Utils.IMDB_RATING;
import static Constants.Utils.SHARED_PREFERENCE;
import static android.content.Context.MODE_PRIVATE;

public class RecentMovies {

    public void getRecentMovies(final Context context) {

        SharedPreferences preferences = context.getSharedPreferences(SHARED_PREFERENCE, MODE_PRIVATE);
        final int IMDB = Integer.parseInt(preferences.getString(IMDB_RATING, "75"));

        ApiInterface apiService = ApiClient.getRetrofit().create(ApiInterface.class);
        Call<List<Movies>> moviesResponseCall = apiService.getAllMovies(1, "year");
        moviesResponseCall.enqueue(new Callback<List<Movies>>() {
            @Override
            public void onResponse(Call<List<Movies>> call, Response<List<Movies>> response) {
                if(response.body() != null) {
                    List<Movies> movies = response.body();
//                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
//                Calendar cal = Calendar.getInstance();


                    try {
                        for (int i = 0; i < movies.size(); i++) {
                            if (movies.get(i).getRating().getPercentage() >= IMDB) {
                                Log.e("sasi", "add " + movies.get(i).getTitle() + " " + movies.get(i).getRating().getPercentage());
//                                recentMoviesList.add(movies.get(i));

                                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(movies.get(i).getTorrents().getEn().get720p().getUrl()));
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                context.startActivity(Intent.createChooser(intent, "Choose Torrent App"));



                                Thread.sleep(500);

                            }
                        }




                    } catch (Exception e) {
                        Log.e("sasi", e.getMessage());
                    }
                }else {
                    Log.e("sasi", "onResponse: error in Json retrival");
                }
            }

            @Override
            public void onFailure(Call<List<Movies>> call, Throwable t) {
                Log.e("sasi", "Error fetching JSON");
            }
        });



    }

}
