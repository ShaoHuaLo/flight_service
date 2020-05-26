CREATE TABLE Users(
username varchar(20) NOT NULL PRIMARY KEY,
password varbinary(20) NOT NULL,
balance int NOT NULL
);

--CREATE TABLE Itinerary(
--iid int identity(1,1) primary key,
--day_of_month int,
--origin_city varchar(20),
--dest_city varchar(20),
--fid1 int,
--fit2 int,
--stop_by varchar(20)
--);


CREATE TABLE Researvation(
rid int,
iid int,
username varchar(20) REFERENCES Users,
paid int,
canceled int,
price int
);
