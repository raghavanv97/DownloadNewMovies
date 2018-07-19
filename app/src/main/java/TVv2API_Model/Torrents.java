package TVv2API_Model;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Torrents {

    @SerializedName("en")
    @Expose
    private En en;

    public En getEn() {
        return en;
    }

    public void setEn(En en) {
        this.en = en;
    }

    public Torrents withEn(En en) {
        this.en = en;
        return this;
    }

}