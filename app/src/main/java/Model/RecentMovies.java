/*
package Model;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import Retrofit.ApiClient;
import Retrofit.ApiInterface;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import Torrent.AddTorrentActivity;

public class RecentMovies extends Thread{
//    private List<Movie> recentMoviesList = new ArrayList<>();
    private final Double IMDB = 7.0;



    public void getRecentMovies(final Context context) {

        ApiInterface apiService = ApiClient.getRetrofit().create(ApiInterface.class);
        Call<MoviesResponse> moviesResponseCall = apiService.getRecentMovies();
        moviesResponseCall.enqueue(new Callback<MoviesResponse>() {
            @Override
            public void onResponse(Call<MoviesResponse> call, Response<MoviesResponse> response) {
                if(response.body() != null) {
                    List<Movie> movies = response.body().getData().getMovies();
//                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
//                Calendar cal = Calendar.getInstance();


                    try {
                        for (int i = 0; i < movies.size(); i++) {
                            if (movies.get(i).getYear() == Calendar.getInstance().get(Calendar.YEAR) && movies.get(i).getRating() >= IMDB) {
                                Log.e("sasi", "add " + movies.get(i).getTitle() + " " + movies.get(i).getRating());
//                                recentMoviesList.add(movies.get(i));
                                */
/*Intent intent = new Intent(Intent.ACTION_VIEW,Uri.parse("magnet:?xt=urn:btih:" + movies.get(i).getTorrents().get(1).getHash() + "&dn=" + movies.get(i).getTitle() + "[1080p]" +
                                        "&tr=udp://open.demonii.com:1337/announce&tr=udp://tracker.openbittorrent.com:80&tr=udp://tracker.coppersurfer.tk:6969&tr=udp://glotorrents.pw:6969/announce&tr=udp://tracker.opentrackr.org:1337/announce&tr=udp://torrent.gresille.org:80/announce&tr=udp://p4p.arenabg.com:1337&tr=udp://tracker.leechers-paradise.org:6969"));
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                context.startActivity(Intent.createChooser(intent, "Choose Torrent App"));*//*


                                Intent intent = new Intent(context.getApplicationContext(), AddTorrentActivity.class);
                                intent.setData(Uri.parse("magnet:?xt=urn:btih:" + movies.get(i).getTorrents().get(1).getHash() + "&dn=" + movies.get(i).getTitle() + "[1080p]" +
                                        "&tr=udp://open.demonii.com:1337/announce&tr=udp://tracker.openbittorrent.com:80&tr=udp://tracker.coppersurfer.tk:6969&tr=udp://glotorrents.pw:6969/announce&tr=udp://tracker.opentrackr.org:1337/announce&tr=udp://torrent.gresille.org:80/announce&tr=udp://p4p.arenabg.com:1337&tr=udp://tracker.leechers-paradise.org:6969"));
                                context.startActivity(intent);


                                Thread.sleep(500);

                            }
                        }


*/
/*
                    for (int i = 0; i < recentMoviesList.size(); i++) {
                        Log.e("rag", recentMoviesList.get(i).getTitle());

                        cal.setTime(sdf.parse(recentMoviesList.get(i).getDateUploaded()));
                        if((cal.get(Calendar.MONTH) == Calendar.getInstance().get(Calendar.MONTH))){
                            Log.e("rag1", recentMoviesList.get(i).getTitle());
                        }
                    }
*//*



                    } catch (Exception e) {
                        Log.e("sasi", e.getMessage());
                    }
                }else {
                    Log.e("sasi", "onResponse: error in Json retrival");
                }
            }

            @Override
            public void onFailure(Call<MoviesResponse> call, Throwable t) {
                Log.e("sasi", "Error fetching JSON");
            }
        });

        try {
            Thread.sleep(1110);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }



}
*/
