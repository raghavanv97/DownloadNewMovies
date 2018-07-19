package TVv2API_Model;


import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class _1080p {

    @SerializedName("provider")
    @Expose
    private String provider;
//    @SerializedName("filesize")
//    @Expose
//    private String filesize;
//    @SerializedName("size")
//    @Expose
//    private Integer size;
    @SerializedName("peer")
    @Expose
    private Integer peer;
    @SerializedName("seed")
    @Expose
    private Integer seed;
    @SerializedName("url")
    @Expose
    private String url;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public _1080p withProvider(String provider) {
        this.provider = provider;
        return this;
    }

//    public String getFilesize() {
//        return filesize;
//    }
//
//    public void setFilesize(String filesize) {
//        this.filesize = filesize;
//    }
//
//    public _1080p withFilesize(String filesize) {
//        this.filesize = filesize;
//        return this;
//    }

//    public Integer getSize() {
//        return size;
//    }

//    public void setSize(Integer size) {
//        this.size = size;
//    }

//    public _1080p withSize(Integer size) {
//        this.size = size;
//        return this;
//    }

    public Integer getPeer() {
        return peer;
    }

    public void setPeer(Integer peer) {
        this.peer = peer;
    }

    public _1080p withPeer(Integer peer) {
        this.peer = peer;
        return this;
    }

    public Integer getSeed() {
        return seed;
    }

    public void setSeed(Integer seed) {
        this.seed = seed;
    }

    public _1080p withSeed(Integer seed) {
        this.seed = seed;
        return this;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public _1080p withUrl(String url) {
        this.url = url;
        return this;
    }

}