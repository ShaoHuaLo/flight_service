package flightapp.dto;

/**
 * a dto class to store data
 */
public class Itinerary {
    public int iid;
    public int fid1;
    public int fid2;
    public int price;
    public int day;

    public Itinerary(int iid, int fid1, int fid2, int price, int day){
        this.iid = iid;
        this.fid1 = fid1;
        this.fid2 = fid2;
        this.price = price;
        this.day = day;
    }
    @Override
    public String toString(){
        return "iid: " + iid + " fid1: " + fid1 + " fid2: " + fid2 + " price: " + price + " day: " +day;
    }

}
