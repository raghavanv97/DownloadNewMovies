package Retrofit;


import com.example.vijayraghavan.monthlynewmovies.MainActivity;

import java.util.List;

import TVv2API_Model.Movies;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiInterface {

//    @GET("list_movies.json")
//    Call<MoviesResponse> getListOfMovies(@Query("limit") int limit);
//
//    @GET("list_movies.json")
//    Call<MoviesResponse> getRecentMovies();




    @GET("movies/{page}")
    Call<List<Movies>> getAllMovies(@Path("page") int page, @Query("sort") String topic);


}
