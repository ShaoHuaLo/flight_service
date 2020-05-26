CREATE TABLE Users(
username varchar(20) NOT NULL PRIMARY KEY,
password varbinary(20) NOT NULL,
balance int NOT NULL
);

CREATE TABLE Researvation(
rid int,
iid int,
username varchar(20) REFERENCES Users,
paid int,
canceled int,
price int
);
