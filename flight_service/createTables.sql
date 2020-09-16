CREATE TABLE Users(
username varchar(20) NOT NULL PRIMARY KEY,
password varbinary(20) NOT NULL,
balance int NOT NULL
);

CREATE TABLE Reservation(
rid int,
iid int,
username varchar(20),
paid int,
canceled int,
price int
);
