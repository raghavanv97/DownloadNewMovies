package TVv2API_Model;

import java.util.List;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Movies {

    @SerializedName("_id")
    @Expose
    private String id;
    @SerializedName("imdb_id")
    @Expose
    private String imdbId;
    @SerializedName("title")
    @Expose
    private String title;
    @SerializedName("year")
    @Expose
    private Integer year;
    @SerializedName("synopsis")
    @Expose
    private String synopsis;
    @SerializedName("runtime")
    @Expose
    private String runtime;
    @SerializedName("released")
    @Expose
    private Integer released;
    @SerializedName("certification")
    @Expose
    private Object certification;
    @SerializedName("torrents")
    @Expose
    private Torrents torrents;
    @SerializedName("trailer")
    @Expose
    private Object trailer;
    @SerializedName("genres")
    @Expose
    private List<Object> genres = null;
    @SerializedName("rating")
    @Expose
    private Rating rating;
    @SerializedName("images")
    @Expose
    private Images images;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Movies withId(String id) {
        this.id = id;
        return this;
    }

    public String getImdbId() {
        return imdbId;
    }

    public void setImdbId(String imdbId) {
        this.imdbId = imdbId;
    }

    public Movies withImdbId(String imdbId) {
        this.imdbId = imdbId;
        return this;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Movies withTitle(String title) {
        this.title = title;
        return this;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public Movies withYear(Integer year) {
        this.year = year;
        return this;
    }

    public String getSynopsis() {
        return synopsis;
    }

    public void setSynopsis(String synopsis) {
        this.synopsis = synopsis;
    }

    public Movies withSynopsis(String synopsis) {
        this.synopsis = synopsis;
        return this;
    }

    public String getRuntime() {
        return runtime;
    }

    public void setRuntime(String runtime) {
        this.runtime = runtime;
    }

    public Movies withRuntime(String runtime) {
        this.runtime = runtime;
        return this;
    }

    public Integer getReleased() {
        return released;
    }

    public void setReleased(Integer released) {
        this.released = released;
    }

    public Movies withReleased(Integer released) {
        this.released = released;
        return this;
    }

    public Object getCertification() {
        return certification;
    }

    public void setCertification(Object certification) {
        this.certification = certification;
    }

    public Movies withCertification(Object certification) {
        this.certification = certification;
        return this;
    }

    public Torrents getTorrents() {
        return torrents;
    }

    public void setTorrents(Torrents torrents) {
        this.torrents = torrents;
    }

    public Movies withTorrents(Torrents torrents) {
        this.torrents = torrents;
        return this;
    }

    public Object getTrailer() {
        return trailer;
    }

    public void setTrailer(Object trailer) {
        this.trailer = trailer;
    }

    public Movies withTrailer(Object trailer) {
        this.trailer = trailer;
        return this;
    }

    public List<Object> getGenres() {
        return genres;
    }

    public void setGenres(List<Object> genres) {
        this.genres = genres;
    }

    public Movies withGenres(List<Object> genres) {
        this.genres = genres;
        return this;
    }

    public Rating getRating() {
        return rating;
    }

    public void setRating(Rating rating) {
        this.rating = rating;
    }

    public Movies withRating(Rating rating) {
        this.rating = rating;
        return this;
    }

    public Images getImages() {
        return images;
    }

    public void setImages(Images images) {
        this.images = images;
    }

    public Movies withImages(Images images) {
        this.images = images;
        return this;
    }

}