package TVv2API_Model;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class En {

    @SerializedName("1080p")
    @Expose
    private TVv2API_Model._1080p _1080p;
    @SerializedName("720p")
    @Expose
    private TVv2API_Model._720p _720p;

    public TVv2API_Model._1080p get1080p() {
        return _1080p;
    }

    public void set1080p(TVv2API_Model._1080p _1080p) {
        this._1080p = _1080p;
    }

    public En with1080p(TVv2API_Model._1080p _1080p) {
        this._1080p = _1080p;
        return this;
    }

    public TVv2API_Model._720p get720p() {
        return _720p;
    }

    public void set720p(TVv2API_Model._720p _720p) {
        this._720p = _720p;
    }

    public En with720p(TVv2API_Model._720p _720p) {
        this._720p = _720p;
        return this;
    }

}