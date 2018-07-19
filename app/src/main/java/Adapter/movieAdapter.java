package Adapter;

import android.content.Context;
import android.content.Intent;
import android.graphics.Movie;
import android.media.Image;
import android.support.annotation.NonNull;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.example.vijayraghavan.monthlynewmovies.R;
import com.example.vijayraghavan.monthlynewmovies.SingleMovie;
import com.squareup.picasso.Picasso;

import java.util.List;


import TVv2API_Model.Movies;

public class movieAdapter extends RecyclerView.Adapter {

    private final int VIEW_TYPE_ITEM = 0;
    private final int VIEW_TYPE_LOADING = 1;
    private boolean mWithHeader = false;
    private boolean mWithFooter = false;
    

    RecyclerView recyclerView;
    Context context;
    private final View.OnClickListener mOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            int position = recyclerView.getChildAdapterPosition(view);
            String title = movies.get(position).getTitle();
//            Toast.makeText(context.getApplicationContext(), title, Toast.LENGTH_LONG).show();
            Intent intent = new Intent(context.getApplicationContext(), SingleMovie.class);
//            intent.putExtra("MovieImage", movies.get(position).getLargeCoverImage());
            intent.putExtra("MovieImage", movies.get(position).getImages().getBanner());


            intent.putExtra("MovieName", movies.get(position).getTitle());

//            intent.putExtra("download720p" , movies.get(position).getTorrents().get(0).getHash());
            String url720p = null;
            try {
                url720p = movies.get(position).getTorrents().getEn().get720p().getUrl();
                if (!url720p.isEmpty())
                    intent.putExtra("download720p", url720p);
            }
            catch (Exception e){
                Log.e("raghavan", "onClick: exception in 720p" );
                intent.putExtra("download720p", "");
            }


//            intent.putExtra("download1080p", movies.get(position).getTorrents().get(1).getHash());

            String url1080p = null;
            try {
                url1080p = movies.get(position).getTorrents().getEn().get1080p().getUrl();
                if (!url1080p.isEmpty())
                    intent.putExtra("download1080p", url1080p);
            }
            catch (Exception e) {
                Log.e("raghavan", "onClick: exception in 1080p" );
                intent.putExtra("download1080p", "");
            }


//            intent.putExtra("imdb", movies.get(position).getRating());
            intent.putExtra("imdb", movies.get(position).getRating().getPercentage());

            intent.putExtra("year", movies.get(position).getYear());
            context.startActivity(intent);
        }
    };
//    private Context context;
    private List<Movies> movies;

    public movieAdapter(List<Movies> movies, RecyclerView recyclerView, Context context){
        this.movies = movies;
        this.recyclerView = recyclerView;
        this.context = context;
    }


    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull final ViewGroup parent, int viewType) {

        RecyclerView.ViewHolder viewholder = null;
        if(viewType == VIEW_TYPE_ITEM) {

            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.single_card_movie, parent, false);
            view.setOnClickListener(mOnClickListener);
            viewholder= new MyViewHolder(view);
        }else if(viewType == VIEW_TYPE_LOADING){
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.loadmore_progress, parent, false);
            viewholder = new ProgressViewHolder(view);
        }

        return viewholder;
    }


    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {

        if(holder instanceof MyViewHolder) {
//        Movie sMovie = movies.get(position);
            Movies sMovie = movies.get(position);

            MyViewHolder myViewHolder = (MyViewHolder) holder;

//            myViewHolder.mTextView.setText(sMovie.getTitle());
//        Picasso.get().load(sMovie.getLargeCoverImage()).into(holder.mImageView);
//        Picasso.get().load(sMovie.getImages().getFanart()).into(holder.mImageView);

            Glide.with(context).load(sMovie.getImages().getBanner()).apply(new RequestOptions().placeholder(R.drawable.movies_logo)).into(myViewHolder.mImageView);
        }else  if(holder instanceof ProgressViewHolder){
            ((ProgressViewHolder) holder).progressBar.setIndeterminate(true);
        }

    }

    @Override
    public int getItemCount() {
        return movies.size();
    }


    public void addMoreMovies(List<Movies> mov){
        movies.addAll(mov);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {

        // loader can't be at position 0
        // loader can only be at the last position

            if (position == getItemCount()) {
                return VIEW_TYPE_LOADING;
            }

        return VIEW_TYPE_ITEM;
    }



}

class MyViewHolder extends RecyclerView.ViewHolder {


    public ImageView mImageView;
//    public TextView mTextView;

    public MyViewHolder(View itemView) {
        super(itemView);
        mImageView = itemView.findViewById(R.id.MovieImage);
//        mTextView = itemView.findViewById(R.id.movieTitle);

    }
}

class ProgressViewHolder extends RecyclerView.ViewHolder {


    public ProgressBar progressBar;

    public ProgressViewHolder(View itemView) {
        super(itemView);
        progressBar = itemView.findViewById(R.id.loadMoreProgress);

    }
}







