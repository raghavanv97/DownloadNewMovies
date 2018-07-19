package com.example.vijayraghavan.monthlynewmovies;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.squareup.picasso.Picasso;

public class SingleMovie extends AppCompatActivity implements View.OnClickListener {

    private TextView movieTitle;
    private ImageView movieImageView;
    private Button download720pButton;
    private Button download1080pButton;
    private String magnetHash720p;
    private String magnetHash1080p;
    private String movieName;
    private TextView rating;
    private TextView year;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_single_movie);
        movieTitle = findViewById(R.id.singleMovieTitle);
        movieImageView = findViewById(R.id.movieImageView);
        download720pButton = findViewById(R.id.download720p);
        download1080pButton= findViewById(R.id.download1080p);
        rating = findViewById(R.id.rating);
        year = findViewById(R.id.year);


        Intent intent = getIntent();
        String movieImage = intent.getStringExtra("MovieImage");
        movieName  = intent.getStringExtra("MovieName");
        magnetHash720p = intent.getStringExtra("download720p");
        magnetHash1080p = intent.getStringExtra("download1080p");
//        Double imdb = intent.getDoubleExtra("imdb", 0.0);
        Integer imdb = intent.getIntExtra("imdb", 0);

        rating.setText("IMBD : " +  imdb);
        year.setText("Year : " + intent.getIntExtra("year", 0));

//        Picasso.get().load(movieImage).into(movieImageView);
        Glide.with(this).load(movieImage).into(movieImageView);


        if(!movieImage.isEmpty())
        movieTitle.setText(movieName);

        if (!magnetHash720p.isEmpty()) {
            download720pButton.setOnClickListener(this);
        }else {
            download720pButton.setEnabled(false);
            download720pButton.setBackgroundColor(Color.RED);
        }
        if(!magnetHash1080p.isEmpty()) {
            download1080pButton.setOnClickListener(this);
        }else {
            download1080pButton.setEnabled(false);
            download1080pButton.setBackgroundColor(Color.RED);

        }


    }

    @Override
    public void onClick(View view) {

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        switch (view.getId()){
            case R.id.download720p:
//                intent.setData(Uri.parse("magnet:?xt=urn:btih:" + magnetHash720p + "&dn=" + movieName + "[720p]" +
//                        "&tr=udp://open.demonii.com:1337/announce&tr=udp://tracker.openbittorrent.com:80&tr=udp://tracker.coppersurfer.tk:6969&tr=udp://glotorrents.pw:6969/announce&tr=udp://tracker.opentrackr.org:1337/announce&tr=udp://torrent.gresille.org:80/announce&tr=udp://p4p.arenabg.com:1337&tr=udp://tracker.leechers-paradise.org:6969"));
                intent.setData(Uri.parse(magnetHash720p));
                break;
            case R.id.download1080p:
//                intent.setData(Uri.parse("magnet:?xt=urn:btih:" + magnetHash1080p + "&dn=" + movieName + "[1080p]" +
//                        "&tr=udp://open.demonii.com:1337/announce&tr=udp://tracker.openbittorrent.com:80&tr=udp://tracker.coppersurfer.tk:6969&tr=udp://glotorrents.pw:6969/announce&tr=udp://tracker.opentrackr.org:1337/announce&tr=udp://torrent.gresille.org:80/announce&tr=udp://p4p.arenabg.com:1337&tr=udp://tracker.leechers-paradise.org:6969"));
                intent.setData(Uri.parse(magnetHash1080p));
                break;

        }
        startActivity(Intent.createChooser(intent, "Choose Torrent..."));

    }
}
