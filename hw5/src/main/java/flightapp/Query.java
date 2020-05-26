package flightapp;

import javax.swing.text.html.HTMLDocument;
import java.io.*;
import java.sql.*;
import java.util.*;

/**
 * Runs queries against a back-end database
 */
public class Query {
    // DB Connection
    private Connection conn;

    // Password hashing parameter constants
    private static final int HASH_STRENGTH = 65536;
    private static final int KEY_LENGTH = 128;

    // TODO: YOUR CODE HERE
    private String currentUser = "";
    public Itinerary[] itineraries;

    public Query() throws SQLException, IOException {
        this(null, null, null, null);
    }

    protected Query(String serverURL, String dbName, String adminName, String password)
            throws SQLException, IOException {
        conn = serverURL == null ? openConnectionFromDbConn()
                : openConnectionFromCredential(serverURL, dbName, adminName, password);

        prepareStatements();
    }

    /**
     * Return a connecion by using dbconn.properties file
     *
     * @throws SQLException
     * @throws IOException
     */
    public static Connection openConnectionFromDbConn() throws SQLException, IOException {
        // Connect to the database with the provided connection configuration
        Properties configProps = new Properties();
        configProps.load(new FileInputStream("dbconn.properties"));
        String serverURL = configProps.getProperty("flightapp.server_url");
        String dbName = configProps.getProperty("flightapp.database_name");
        String adminName = configProps.getProperty("flightapp.username");
        String password = configProps.getProperty("flightapp.password");
        return openConnectionFromCredential(serverURL, dbName, adminName, password);
    }

    /**
     * Return a connecion by using the provided parameter.
     *
     * @param serverURL example: example.database.widows.net
     * @param dbName    database name
     * @param adminName username to login server
     * @param password  password to login server
     *
     * @throws SQLException
     */
    protected static Connection openConnectionFromCredential(String serverURL, String dbName,
                                                             String adminName, String password) throws SQLException {
        String connectionUrl =
                String.format("jdbc:sqlserver://%s:1433;databaseName=%s;user=%s;password=%s", serverURL,
                        dbName, adminName, password);
        Connection conn = DriverManager.getConnection(connectionUrl);

        // By default, automatically commit after each statement
        conn.setAutoCommit(true);

        // By default, set the transaction isolation level to serializable
        conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);

        return conn;
    }

    public Connection getConnection() {
        return conn;
    }

    public void closeConnection() throws SQLException {
        conn.close();
    }



    private static final String Begin_Transaction = "SET TRANSACTION ISOLATION LEVEL SERIALIZABLE; BEGIN TRANSACTION;";
    private PreparedStatement beginTransactionStatement;

    private static final String Commit = "COMMIT TRANSACTION";
    private PreparedStatement commitStatement;

    private static final String Roll_Back = "ROLLBACK TRANSACTION";
    private PreparedStatement rollbackStatement;

    // Canned queries
    private static final String CHECK_FLIGHT_CAPACITY = "SELECT capacity FROM Flights WHERE fid = ?";
    private PreparedStatement checkFlightCapacityStatement;
    // For check dangling
    private static final String TRANCOUNT_SQL = "SELECT @@TRANCOUNT AS tran_count";
    private PreparedStatement tranCountStatement;


    private void prepareStatements() throws SQLException {
        checkFlightCapacityStatement = conn.prepareStatement(CHECK_FLIGHT_CAPACITY);
        tranCountStatement = conn.prepareStatement(TRANCOUNT_SQL);
        beginTransactionStatement = conn.prepareStatement(Begin_Transaction);
        commitStatement = conn.prepareStatement(Commit);
        rollbackStatement = conn.prepareStatement(Roll_Back);

        // TODO: YOUR CODE HERE
    }

    /**
     * Takes a user's username and password and attempts to log the user in.
     *
     * @param username user's username
     * @param password user's password
     *
     * @return If someone has already logged in, then return "User already logged in\n" For all other
     *         errors, return "Login failed\n". Otherwise, return "Logged in as [username]\n".
     */
    /**
     * Clear the data in any custom tables created.
     *
     * WARNING! Do not drop any tables and do not clear the flights table.
     */
    public void clearTables() {

        try {
            beginTransactionStatement.executeUpdate();

            String clearTables = "TRUNCATE TABLE Users" +
                    "TRUNCATE TABLE Itinerary" + "TRUNCATE TABLE Reservation";
            PreparedStatement clearTableStatement =conn.prepareStatement(clearTables);

            clearTableStatement.executeUpdate();

            commitStatement.executeUpdate();

            // TODO: YOUR CODE HERE
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public String transaction_login(String username, String password) {

        if(this.currentUser == username){
            return "User already logged in\n";
        }
        try {
            String login = "SELECT * FROM Users WHERE username = ? AND password = ?;";
            PreparedStatement loginPs = conn.prepareStatement(login);

            byte[] passwordBytes = password.getBytes();              //casting to varbinary
            loginPs.clearParameters();
            loginPs.setString(1, username);
            loginPs.setBytes(2, passwordBytes);

            ResultSet result = loginPs.executeQuery();
            if (result.next() == false) {
                return "Login failed\n";
            }

            currentUser = username;

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return " Logged in as " + this.currentUser + "\n";
    }

    /**
     * Implement the create user function.
     *
     * @param username   new user's username. User names are unique the system.
     * @param password   new user's password.
     * @param initAmount initial amount to deposit into the user's account, should be >= 0 (failure
     *                   otherwise).
     *
     * @return either "Created user {@code username}\n" or "Failed to create user\n" if failed.
     */
    public String transaction_createCustomer(String username, String password, int initAmount) {
        try {
            if(initAmount < 0){
                return "Failed to create user, initAmount should be positive\n";
            }

            byte[] passwordBytes = password.getBytes();

            String checkValidPassword = "SELECT * FROM Users WHERE password = ?";
            PreparedStatement checkValidPasswordPs = conn.prepareStatement(checkValidPassword);
            checkValidPasswordPs.setBytes(1,passwordBytes);

            checkValidPasswordPs.clearParameters();
            ResultSet result = checkValidPasswordPs.executeQuery();

            if(result.next()){
                return "password has already existed, please change another\n";
            }
            result.close();


            beginTransactionStatement.executeUpdate();

            String createCustomer = "INSERT INTO Users VALUE(?,?,?)";
            PreparedStatement createCustomerPs = conn.prepareStatement(createCustomer);

            createCustomerPs.clearParameters();
            createCustomerPs.setString(1,username);

            createCustomerPs.setBytes(2,passwordBytes);
            createCustomerPs.setInt(3,initAmount);

            int resultRows = createCustomerPs.executeUpdate();
            commitStatement.executeUpdate();
            if(resultRows==0){
                return "Failed to create user\n";
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "Failed to create user\n";
        } finally {
            checkDanglingTransaction();
        }
        return "Created user: "+username+ "\n";
    }

    /**
     * Implement the search function.
     *
     * Searches for flights from the given origin city to the given destination city, on the given day
     * of the month. If {@code directFlight} is true, it only searches for direct flights, otherwise
     * is searches for direct flights and flights with two "hops." Only searches for up to the number
     * of itineraries given by {@code numberOfItineraries}.
     *
     * The results are sorted based on total flight time.
     *
     * @param originCity
     * @param destinationCity
     * @param directFlight        if true, then only search for direct flights, otherwise include
     *                            indirect flights as well
     * @param dayOfMonth
     * @param numberOfItineraries number of itineraries to return
     *
     * @return If no itineraries were found, return "No flights match your selection\n". If an error
     *         occurs, then return "Failed to search\n".
     *
     *         Otherwise, the sorted itineraries printed in the following format:
     *
     *         Itinerary [itinerary number]: [number of flights] flight(s), [total flight time]
     *         minutes\n [first flight in itinerary]\n ... [last flight in itinerary]\n
     *
     *         Each flight should be printed using the same format as in the {@code Flight} class.
     *         Itinerary numbers in each search should always start from 0 and increase by 1.
     *
     * @see Flight#toString()
     */


    public String transaction_search(String originCity, String destinationCity, boolean directFlight, int dayOfMonth,
                                     int numberOfItineraries)
    {
        StringBuffer sb = new StringBuffer();
        itineraries = new Itinerary[numberOfItineraries];

        try {
            int count = 0;
            // df for direct flights
            String dfQuery = "SELECT TOP (?) fid,day_of_month_id,carrier_id,flight_num,origin_city,dest_city,actual_time,capacity,price "
                    + "FROM Flights "
                    + "WHERE origin_city like ? AND dest_city like ? AND day_of_month_id = ? AND canceled = 0 "
                    + "ORDER BY actual_time ASC, fid ASC";
            PreparedStatement ps = conn.prepareStatement(dfQuery);

            ps.clearParameters();
            ps.setInt(1, numberOfItineraries);
            ps.setString(2, "%"+originCity+"%");
            ps.setString(3, "%"+destinationCity+"%");
            ps.setInt(4, dayOfMonth);

            ResultSet df = ps.executeQuery();

            while (df.next()) {
                int result_fid = df.getInt("fid");
                //System.out.println(result_fid);
                int result_dayOfMonth = df.getInt("day_of_month_id");
                String result_carrierId = df.getString("carrier_id");
                String result_flightNum = df.getString("flight_num");
                String result_originCity = df.getString("origin_city");
                String result_destCity = df.getString("dest_city");
                int result_time = df.getInt("actual_time");
                int result_capacity = df.getInt("capacity");
                int result_price = df.getInt("price");

                sb.append("Itinerary ").append(count)
                        .append(": 1 flight(s), ").append(result_time)
                        .append(" minutes\n")
                        .append("ID: ").append(result_fid)
                        .append(" Day: ").append(result_dayOfMonth)
                        .append(" Carrier: ").append(result_carrierId)
                        .append(" Number: ").append(result_flightNum)
                        .append(" Origin: ").append(result_originCity)
                        .append(" Dest: ").append(result_destCity)
                        .append(" Duration: ").append(result_time)
                        .append(" Capacity: ").append(result_capacity)
                        .append(" Price: ").append(result_price)
                        .append("\n");

                itineraries[count] = new Itinerary(count, result_fid, -1, result_price, result_dayOfMonth);

                count++;
            }
            df.close();

            boolean executeIndf = count < numberOfItineraries & !directFlight;
            if (executeIndf) {
                int totalIndf = numberOfItineraries - count;
                String indfQuery = "SELECT TOP (?) F1.fid,F1.day_of_month_id,F1.carrier_id,F1.flight_num,F1.origin_city,F1.dest_city,F1.actual_time,F1.capacity,F1.price,"
                        + "F2.fid,F2.carrier_id,F2.flight_num,F2.origin_city,F2.dest_city,F2.actual_time,F2.capacity,F2.price "
                        + "FROM Flights AS F1, Flights AS F2 "
                        + "WHERE F1.origin_city = ? AND F1.dest_city = F2.origin_city AND F2.dest_city = ? "
                        + "AND F1.day_of_month_id = F2.day_of_month_id AND F1.month_id = F2.month_id AND F1.day_of_month_id = ? "
                        + "AND F1.canceled = 0 AND F2.canceled = 0 "
                        + "ORDER BY (F1.actual_time + F2.actual_time) ASC, F1.fid ASC, F2.fid ASC";

                ps = conn.prepareStatement(indfQuery);
                ps.clearParameters();
                ps.setInt(1, totalIndf);
                ps.setString(2, originCity);
                ps.setString(3, destinationCity);
                ps.setInt(4, dayOfMonth);

                ResultSet indf = ps.executeQuery();

                while (indf.next()) {
                    int r_fid1 = indf.getInt(1);
                    int r_fid2 = indf.getInt(10);
                    int r_dayOfMonth = indf.getInt(2);
                    String r_carrier1 = indf.getString(3);
                    String r_carrier2 = indf.getString(11);
                    String r_flightNum1 = indf.getString(4);
                    String r_flightNum2 = indf.getString(12);
                    String r_org1 = indf.getString(5);
                    String r_org2 = indf.getString(13);
                    String r_dest1 = indf.getString(6);
                    String r_dest2 = indf.getString(14);
                    int r_time1 = indf.getInt(7);
                    int r_time2 = indf.getInt(15);
                    int r_cap1 = indf.getInt(8);
                    int r_cap2 = indf.getInt(16);
                    int r_price1 = indf.getInt(9);
                    int r_price2 = indf.getInt(17);

                    sb.append("Itinerary ").append(count)
                            .append(": 2 flight(s), ").append(r_time1 + r_time2)
                            .append(" minutes\n")
                            .append("ID: ").append(r_fid1)
                            .append(" Day: ").append(r_dayOfMonth)
                            .append(" Carrier: ").append(r_carrier1)
                            .append(" Number: ").append(r_flightNum1)
                            .append(" Origin: ").append(r_org1)
                            .append(" Dest: ").append(r_dest1)
                            .append(" Duration: ").append(r_time1)
                            .append(" Capacity: ").append(r_cap1)
                            .append(" Price: ").append(r_price1)
                            .append("\n")
                            .append("ID: ").append(r_fid2)
                            .append(" Day: ").append(r_dayOfMonth)
                            .append(" Carrier: ").append(r_carrier2)
                            .append(" Number: ").append(r_flightNum2)
                            .append(" Origin: ").append(r_org2)
                            .append(" Dest: ").append(r_dest2)
                            .append(" Duration: ").append(r_time2)
                            .append(" Capacity: ").append(r_cap2)
                            .append(" Price: ").append(r_price2)
                            .append("\n");

                    itineraries[count] = new Itinerary(count, r_fid1, r_fid2, r_price1 + r_price2, r_dayOfMonth);

                    count++;
                }
                indf.close();
            }

            if (count == 0) {
                sb.append("No flights match your selection\n");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return sb.toString();
    }





    /**
     * Implements the book itinerary function.
     *
     * @param itineraryId ID of the itinerary to book. This must be one that is returned by search in
     *                    the current session.
     *
     * @return If the user is not logged in, then return "Cannot book reservations, not logged in\n".
     *         If the user is trying to book an itinerary with an invalid ID or without having done a
     *         search, then return "No such itinerary {@code itineraryId}\n". If the user already has
     *         a reservation on the same day as the one that they are trying to book now, then return
     *         "You cannot book two flights in the same day\n". For all other errors, return "Booking
     *         failed\n".
     *
     *         And if booking succeeded, return "Booked flight(s), reservation ID: [reservationId]\n"
     *         where reservationId is a unique number in the reservation system that starts from 1 and
     *         increments by 1 each time a successful reservation is made by any user in the system.
     */
    public String transaction_book(int itineraryId) {


        if(this.currentUser == null){
            return "booking failed, not logged in!\n";
        }

        if(itineraries == null){
            return "booking failed" + "no such itineraryId: " + itineraryId + "\nyou have to search for valid itineraryId first!" +"\n";
        }

        int index = 0;
        boolean validId = false;
        try{
            while(itineraries[index] != null){
                if(itineraries[index].iid == itineraryId){
                    validId = true;
                    break;
                }
                index++;
            }
        }catch(ArrayIndexOutOfBoundsException e){
            return "booking failed" + "no such itineraryId: " + itineraryId + "\n";
        }
        if(!validId){
            return "booking failed" + "no such itineraryId: " + itineraryId + "\n";
        }

        Itinerary book = itineraries[index];    //itinerary(iid, fid1, fid2, price, day)

        //check capacity
        try{
            int cap1 = 0;
            int cap2 = 0;
            conn.setAutoCommit(false);
            beginTransactionStatement.executeUpdate();

            //first flight check

            checkFlightCapacityStatement.clearParameters();
            checkFlightCapacityStatement.setInt(1,book.fid1);

            ResultSet result = checkFlightCapacityStatement.executeQuery();
            result.next();               //result should include only 1 row, moving the cursor to the first row
            cap1 = result.getInt(1);

            if(cap1<1){
                rollbackStatement.executeUpdate();
                conn.setAutoCommit(true);
                result.close();
                return "this flight is full, booking failed\n";
            }

            if(book.fid2 != -1){
                checkFlightCapacityStatement.clearParameters();
                checkFlightCapacityStatement.setInt(1,book.fid2);
                result = checkFlightCapacityStatement.executeQuery();
                result.next();
                cap2 = result.getInt(1);
                if(cap2<1){
                    rollbackStatement.executeUpdate();
                    conn.setAutoCommit(true);
                    result.close();
                    return "second flight is full, book failed\n";
                }
            }

            //update flights
            String updateFlights = "UPDATE Flights SET capacity = ? WHERE fid = ?;\n";
            PreparedStatement updateFlightsPs = conn.prepareStatement(updateFlights);
            updateFlightsPs.clearParameters();
            updateFlightsPs.setInt(1,cap1-1);
            updateFlightsPs.setInt(2,book.fid1);
            updateFlightsPs.executeUpdate();

            if(book.fid2 != -1){
                updateFlightsPs.clearParameters();
                updateFlightsPs.setInt(1,cap2-1);
                updateFlightsPs.setInt(2,book.fid2);
                updateFlightsPs.executeUpdate();
            }


            //update reservation
            String updateReservation = "INSERT INTO Reservation VALUES(?,?,?,?,?,?);\n"; //rid, iid, username, paid, canceled, price
            PreparedStatement updateReservationPs = conn.prepareStatement(updateReservation);

            //get rid
            String numberOfReservation = "SELECT COUNT(*) FROM Reservation;\n";
            PreparedStatement numberOfReservationPs = conn.prepareStatement(numberOfReservation);
            result = numberOfReservationPs.executeQuery();
            result.next();
            int rid =  result.getInt(1); //reservation id beginning from 0

            updateReservationPs.setInt(1,rid); //rid
            updateReservationPs.setInt(2,itineraryId); //iid
            updateReservationPs.setString(3,this.currentUser); //user
            updateReservationPs.setInt(4,0); //paid
            updateReservationPs.setInt(5,0); //canceled
            updateReservationPs.setInt(6,book.price);//price
            updateReservationPs.executeUpdate();

            commitStatement.executeUpdate();
            conn.setAutoCommit(true);
            result.close();
            return "Booked flight(s), reservation ID: " + rid + "\n";

        }catch(SQLException e) {
            e.printStackTrace();
            return "booking failed\n";
        }
    }




    /**
     * Implements the pay function.

     * @param reservationId the reservation to pay for.
     *
     * @return If no user has logged in, then return "Cannot pay, not logged in\n" If the reservation
     *         is not found / not under the logged in user's name, then return "Cannot find unpaid
     *         reservation [reservationId] under user: [username]\n" If the user does not have enough
     *         money in their account, then return "User has only [balance] in account but itinerary
     *         costs [cost]\n" For all other errors, return "Failed to pay for reservation
     *         [reservationId]\n"
     *
     *         If successful, return "Paid reservation: [reservationId] remaining balance:
     *         [balance]\n" where [balance] is the remaining balance in the user's account.
     */
    public String transaction_pay(int reservationId) {

        // TODO: YOUR CODE HERE
        try {
            conn.setAutoCommit(false);
            beginTransactionStatement.executeUpdate();
            //check validity of reservationId
            String checkRid = "SELECT rid, price FROM Reservation WHERE rid = ? AND username = ?;";
            PreparedStatement checkRidPs = conn.prepareStatement(checkRid);

            checkRidPs.clearParameters();
            checkRidPs.setInt(1,reservationId);
            checkRidPs.setString(2, this.currentUser);

            ResultSet result = checkRidPs.executeQuery();

            if(!result.next()){
                rollbackStatement.executeUpdate();
                conn.setAutoCommit(true);
                result.close();
                return "the reservation: "+ reservationId + " is not found";
            }
            //get the payment due


            int paymentDue = result.getInt(2);


            //check balance
            String checkbalance = "SELECT balance FROM Users WHERE username = ?;";
            PreparedStatement checkbalanceStatement = conn.prepareStatement(checkbalance);
            checkbalanceStatement.clearParameters();
            checkbalanceStatement.setString(1, this.currentUser);

            result = checkbalanceStatement.executeQuery();
            result.next();
            int balance = result.getInt(1);
            if (balance < paymentDue) {
                rollbackStatement.executeUpdate();
                conn.setAutoCommit(true);
                result.close();
                return "balance is not enough to cover the payment of "+ paymentDue + "\ncurrent balance: "
                        + balance +"\n";
            }

            //update user balance
            String updateUser = "UPDATE Users SET balance = ? WHERE username = ?;";
            PreparedStatement updateUserPs = conn.prepareStatement(updateUser);
            updateUserPs.clearParameters();
            updateUserPs.setInt(1, balance - paymentDue);
            updateUserPs.setString(2, this.currentUser);
            updateUserPs.executeUpdate();


            //update reservation
            String updateReservation = "UPDATE Reservation SET paid = ? WHERE rid = ?;";
            PreparedStatement updateReservationPs = conn.prepareStatement(updateReservation);

            updateReservationPs.clearParameters();
            updateReservationPs.setInt(1, 1);
            updateReservationPs.setInt(2, reservationId);
            updateReservationPs.executeUpdate();
            commitStatement.executeUpdate();
            conn.setAutoCommit(true);
            result.close();

            return "payment succeed, payement: " + paymentDue + " ReservationID: " + reservationId;
        } catch (SQLException e) {
            e.printStackTrace();
            return "failed to pay the reservation";
        }
    }






    /**
     * Implements the reservations function.
     *
     * @return If no user has logged in, then return "Cannot view reservations, not logged in\n" If
     *         the user has no reservations, then return "No reservations found\n" For all other
     *         errors, return "Failed to retrieve reservations\n"
     *
     *         Otherwise return the reservations in the following format:
     *
     *         Reservation [reservation ID] paid: [true or false]:\n [flight 1 under the
     *         reservation]\n [flight 2 under the reservation]\n Reservation [reservation ID] paid:
     *         [true or false]:\n [flight 1 under the reservation]\n [flight 2 under the
     *         reservation]\n ...
     *
     *         Each flight should be printed using the same format as in the {@code Flight} class.
     *
     * @see Flight#toString()
     */
//    public String transaction_reservations() {
//        if(this.currentUser == null){
//            return "Cannot view reservations, not logged in\n";
//        }
//
//        try {
//            // TODO: YOUR CODE HERE
//                boolean found = false;
//                StringBuffer sb = new StringBuffer();
//                String reservationQuery = "SELECT rid FROM Reservation WHERE user = ?";
//            return "Failed to retrieve reservations\n";
//            } finally {
//            checkDanglingTransaction();
//            }
//    }

    public String transaction_reservations()
    {
        if (this.currentUser == null) {
            return "Cannot view reservations, not logged in\n";
        }

        try {
            beginTransactionStatement.executeUpdate();

            String resQuery = "SELECT rid, paid, fid1, fid2 FROM Reservations WHERE username = ?;\n";
            PreparedStatement ps = conn.prepareStatement(resQuery);
            ps.clearParameters();
            ps.setString(1, this.currentUser);
            ResultSet rs = ps.executeQuery();

            StringBuffer sb = new StringBuffer();
            while (rs.next()) {
                int r_rid = rs.getInt(1);
                int r_paid = rs.getInt(2);
                int r_fid1 = rs.getInt(3);
                int r_fid2 = rs.getInt(4);
                String paid = (r_paid == 1) ? "true" : "false";

                sb.append("Reservation ").append(r_rid).append(" paid: ").append(paid).append(":\n");

                String flightQuery = "SELECT fid, day_of_month_id, carrier_id, flight_num, origin_city,"
                        + "dest_city, actual_time, capacity, price FROM Flights WHERE fid = ?;\n";
                ps = conn.prepareStatement(flightQuery);
                ps.clearParameters();
                ps.setInt(1, r_fid1);
                ResultSet frs = ps.executeQuery();  // flight resultset
                frs.next();

                int rf_fid = frs.getInt(1);  // rf for result [from] flight
                int rf_day_of_month_id = frs.getInt(2);
                String rf_cid = frs.getString(3);
                int rf_flight_num = frs.getInt(4);
                String rf_org_city = frs.getString(5);
                String rf_dest_city = frs.getString(6);
                int rf_actual_time = frs.getInt(7);
                int rf_capacity = frs.getInt(8);
                int rf_price = frs.getInt(9);

                sb.append("ID: ").append(rf_fid).append(" Day: ").append(rf_day_of_month_id)
                        .append(" Carrier: ").append(rf_cid).append(" Number: ").append(rf_flight_num)
                        .append(" Origin: ").append(rf_org_city).append(" Dest: ").append(rf_dest_city)
                        .append(" Duration: ").append(rf_actual_time).append(" Capacity: ").append(rf_capacity)
                        .append(" Price: ").append(rf_price).append("\n");

                if (r_fid2 != -1) {
                    ps = conn.prepareStatement(flightQuery);
                    ps.clearParameters();
                    ps.setInt(1, r_fid2);
                    frs = ps.executeQuery();
                    frs.next();

                    rf_fid = frs.getInt(1);
                    rf_day_of_month_id = frs.getInt(2);
                    rf_cid = frs.getString(3);
                    rf_flight_num = frs.getInt(4);
                    rf_org_city = frs.getString(5);
                    rf_dest_city = frs.getString(6);
                    rf_actual_time = frs.getInt(7);
                    rf_capacity = frs.getInt(8);
                    rf_price = frs.getInt(9);

                    sb.append("ID: ").append(rf_fid).append(" Day: ").append(rf_day_of_month_id)
                            .append(" Carrier: ").append(rf_cid).append(" Number: ").append(rf_flight_num)
                            .append(" Origin: ").append(rf_org_city).append(" Dest: ").append(rf_dest_city)
                            .append( "Duration: ").append(rf_actual_time).append(" Capacity: ").append(rf_actual_time)
                            .append(" Price: ").append(rf_price).append("\n");

                    frs.close();
                }
            }
            rs.close();
            if (sb.toString().equals("")) {
                rollbackStatement.executeUpdate();
                return "No reservations found\n";
            } else {
                commitStatement.executeUpdate();
                conn.setAutoCommit(true);
                return sb.toString();
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return "Failed to retrieve reservations\n";
        }
    }

    /**
     * Implements the cancel operation.
     *
     * @param reservationId the reservation ID to cancel
     *
     * @return If no user has logged in, then return "Cannot cancel reservations, not logged in\n" For
     *         all other errors, return "Failed to cancel reservation [reservationId]\n"
     *
     *         If successful, return "Canceled reservation [reservationId]\n"
     *
     *         Even though a reservation has been canceled, its ID should not be reused by the system.
     */
    public String transaction_cancel(int reservationId) {
            try {
            // TODO: YOUR CODE HERE
            return "Failed to cancel reservation " + reservationId + "\n";
            } finally {
            checkDanglingTransaction();
            }
            }

    /**
     * Example utility function that uses prepared statements
     */
    private int checkFlightCapacity(int fid) throws SQLException {
            checkFlightCapacityStatement.clearParameters();
            checkFlightCapacityStatement.setInt(1, fid);
            ResultSet results = checkFlightCapacityStatement.executeQuery();
            results.next();
            int capacity = results.getInt("capacity");
            results.close();

            return capacity;
            }

    /**
     * Throw IllegalStateException if transaction not completely complete, rollback.
     *
     */
    private void checkDanglingTransaction() {
            try {
            try (ResultSet rs = tranCountStatement.executeQuery()) {
            rs.next();
            int count = rs.getInt("tran_count");
            if (count > 0) {
            throw new IllegalStateException(
            "Transaction not fully commit/rollback. Number of transaction in process: " + count);
            }
            } finally {
            conn.setAutoCommit(true);
            }
            } catch (SQLException e) {
            throw new IllegalStateException("Database error", e);
            }
            }

    private static boolean isDeadLock(SQLException ex) {
            return ex.getErrorCode() == 1205;
            }

    /**
     * A class to store flight information.
     */
    class Flight {
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

    class Itinerary {
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
            return "iid" + iid;
        }
    }

}




