CREATE TABLE User(
username varchar(20) NOT NULL PRIMARY KEY,
password varbinary(20) NOT NULL,
balance int NOT NULL
);

CREATE TABLE Itinerary(
iid int identity(1,1) primary key,
day_of_month int,
origin_city varchar(20),
dest_city varchar(20)
);


CREATE TABLE Researvation(
rid int,
paid int,
fit1 int,
fid2 int,
username varchar(20) REFERENCES Users,
price int
);

create table Flights(
fid int primary key,
month_id int not null references Months, -- 1-12
day_of_month int not null, -- 1-31
day_of_week_id int not null references Weekdays, -- 1-7, 1 = Monday, 2 = Tuesday, etc carrier_id varchar(7) not null references Carriers,
carrier_id varchar(7),
flight_num int not null,
origin_city varchar(34) not null,
origin_state varchar(47) not null,
dest_city varchar(34) not null,
dest_state varchar(46) not null,
departure_delay int not null, -- in mins
taxi_out int not null, -- in mins
arrival_delay int not null, -- in mins
canceled int not null, -- 1 means canceled
actual_time int not null, -- in mins
distance int not null, -- in miles
capacity int not null,
price int not null);
