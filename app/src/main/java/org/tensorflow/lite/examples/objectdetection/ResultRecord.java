package org.tensorflow.lite.examples.objectdetection;

public class ResultRecord {
    private String item;
    private float score;

    public ResultRecord(String item, float score) {
        this.item = item;
        this.score = score;
    }

    public String getItem() {
        return item;
    }

    public void setItem(String item) {
        this.item = item;
    }

    public float getScore() {
        return score;
    }

    public void setScore(float score) {
        this.score = score;
    }

    @Override
    public String toString() {
        return "ResultRecord{" +
                "item='" + item + '\'' +
                ", score=" + score +
                '}';
    }
}
