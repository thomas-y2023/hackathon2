package org.tensorflow.lite.examples.objectdetection;

import java.util.List;

public class AdRecord {
    public List<String> tags;
    public String url;

    public AdRecord(String url, List<String> tags) {
        this.url = url;
        this.tags = tags;
    }
}
