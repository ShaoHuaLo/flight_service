package flightapp.dto;


/**
 * A class to store flight information.
 */
public class Flight {

    public int fid;
    public int dayOfMonth;
    public String carrierId;
    public String flightNum;
    public String originCity;
    public String destCity;
    public int time;
    public int capacity;
    public int price;

    @Override
    public String toString() {
        return "ID: " + fid + " Day: " + dayOfMonth + " Carrier: " + carrierId + " Number: "
                + flightNum + " Origin: " + originCity + " Dest: " + destCity + " Duration: " + time
                + " Capacity: " + capacity + " Price: " + price;
    }


}
