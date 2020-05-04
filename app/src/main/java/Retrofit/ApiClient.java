package Retrofit;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {

//    private static final String BASE_URL = "https://yts.am/api/v2/";
    static int pages = 1;
//private static final String BASE_URL = "https://tv-v2.api-fetch.website";
private static final String BASE_URL = "https://movies-v2.api-fetch.sh";
    private static Retrofit retrofit = null;


    public static Retrofit getRetrofit(){
        if(retrofit == null)
            retrofit =  new Retrofit.Builder().baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create()).build();

        return retrofit;

    }

}
