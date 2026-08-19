-- Bang khoa cua ShedLock. DDL lay tu README chinh thuc cua ShedLock cho Postgres.
--
-- Bang do cinebook-api tao vi api so huu schema, nhung nguoi dung no la cinebook-worker:
-- sweeper va job doi soat chi duoc chay o mot instance tai mot thoi diem. Outbox relay
-- CO Y khong dung bang nay — no da co FOR UPDATE SKIP LOCKED.
CREATE TABLE shedlock (
    name       varchar(64)  NOT NULL PRIMARY KEY,
    lock_until timestamp    NOT NULL,
    locked_at  timestamp    NOT NULL,
    locked_by  varchar(255) NOT NULL
);
