package TVv2API_Model;


import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Rating {

    @SerializedName("percentage")
    @Expose
    private Integer percentage;
    @SerializedName("watching")
    @Expose
    private Integer watching;
    @SerializedName("votes")
    @Expose
    private Integer votes;
    @SerializedName("loved")
    @Expose
    private Integer loved;
    @SerializedName("hated")
    @Expose
    private Integer hated;

    public Integer getPercentage() {
        return percentage;
    }

    public void setPercentage(Integer percentage) {
        this.percentage = percentage;
    }

    public Rating withPercentage(Integer percentage) {
        this.percentage = percentage;
        return this;
    }

    public Integer getWatching() {
        return watching;
    }

    public void setWatching(Integer watching) {
        this.watching = watching;
    }

    public Rating withWatching(Integer watching) {
        this.watching = watching;
        return this;
    }

    public Integer getVotes() {
        return votes;
    }

    public void setVotes(Integer votes) {
        this.votes = votes;
    }

    public Rating withVotes(Integer votes) {
        this.votes = votes;
        return this;
    }

    public Integer getLoved() {
        return loved;
    }

    public void setLoved(Integer loved) {
        this.loved = loved;
    }

    public Rating withLoved(Integer loved) {
        this.loved = loved;
        return this;
    }

    public Integer getHated() {
        return hated;
    }

    public void setHated(Integer hated) {
        this.hated = hated;
    }

    public Rating withHated(Integer hated) {
        this.hated = hated;
        return this;
    }

}