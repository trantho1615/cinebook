# Load test

Kich ban k6 mo phong mot buoi flash sale: rat nhieu nguoi mo cung mot suat chieu, mot nhom
gianh nhau cung mot vung ghe hep, mot nhom nho thanh toan.

## Chay

Can hai thu chay truoc: ha tang va ung dung.

```bash
docker compose up -d
mvn -B -q -DskipTests package
java -jar cinebook-api/target/cinebook-api-0.1.0-SNAPSHOT-exec.jar --spring.profiles.active=demo
java -jar cinebook-worker/target/cinebook-worker-0.1.0-SNAPSHOT.jar
```

Profile `demo` la bat buoc: khong co no thi khong co suat chieu nao trong tuong lai va
kich ban dung ngay o buoc setup.

Roi chay k6 trong container — khong can cai gi len may:

```bash
docker run --rm -i -v "$PWD/load-test:/scripts" grafana/k6:1.5.0 run /scripts/flash-sale.js
```

Tren Git Bash cho Windows, them `MSYS_NO_PATHCONV=1` o dau lenh: khong thi `/scripts/...`
bi doi thanh duong dan Windows va k6 bao khong tim thay file.

Doi dia chi neu can:

```bash
docker run --rm -i -e BASE_URL=http://host.docker.internal:8080 ... 
```

## Doc ket qua o dau

- Ket qua tung lan chay: k6 in ra ngay o terminal.
- Bieu do trong luc chay: Grafana http://localhost:3000, dashboard "cinebook".
- So lieu da ghi lai kem dieu kien do: `docs/ket-qua-do-tai.md`.

## Hai dieu de tu lua minh

**409 khong phai loi.** `SEATS_UNAVAILABLE` la ket qua nghiep vu dung dan trong kich ban
nay. Kich ban dat `http.setResponseCallback(http.expectedStatuses(200, 201, 204, 409))` o
init context — dat trong `options` thi khong co tac dung, va `http_req_failed` se bang
dung ti le xung dot.

**Vung ghe hep la co y.** Trai deu 96 ghe thi gan nhu khong ai dung ai va con so thu duoc
se dep mot cach vo nghia. Cho gianh nhau moi la thu dang do.

## Sinh du lieu o quy mo lon

`flash-sale.js` chay tren du lieu cua profile `demo`: ~477 suat chieu, 864 ghe. O kich thuoc
do khong index nao kip co y nghia — Postgres seq scan vai tram dong con nhanh hon di qua
index, nen `EXPLAIN ANALYZE` khong noi duoc dieu gi.

`seed-large.sql` nap du lieu o quy mo mot chuoi rap sau nhieu nam:

```bash
docker exec -i cinebook-postgres psql -U cinebook -d cinebook \
  -v st_per_room=22222 -v expired_per_st=50 -v booked_per_st=10 \
  -v n_users=50000 -v n_bookings=2000000 -v n_outbox=2000000 \
  -f - < load-test/seed-large.sql
```

Thu o quy mo nho truoc: `-v st_per_room=100 -v n_bookings=5000 -v n_outbox=5000`.

Xoa di, giu nguyen du lieu demo:

```bash
docker exec -i cinebook-postgres psql -U cinebook -d cinebook -f - < load-test/seed-large-reset.sql
```

### Ba dieu script nay phai ton trong, va vi sao

**Ghe phai thuoc dung phong cua suat chieu.** `seat_hold.seat_id` chi co khoa ngoai toi
`seats`, khong ai ep no phai cung phong voi suat chieu. Sinh sai thi truy van seat map
khong join duoc dong nao: bang co 12 trieu dong ma duong nong khong cham toi dong nao, va
ca bai do thanh vo nghia. Bang `seed_ref_room_seat` giu dung rang buoc do.

**Suat chieu khong duoc chong gio trong cung mot phong.** `showtimes` co
`EXCLUDE USING gist (room_id WITH =, tstzrange(start_at, end_at) WITH &&)`. Rai gio ngau
nhien la vi pham ngay. Moi phong duoc cap mot day khe tuan tu cach nhau 3 gio, suat dai 2
gio. Toan bo nam trong qua khu xa de khong dung vao du lieu demo quanh hien tai.

**`ANALYZE` la bat buoc, khong phai tuy chon.** Bo qua thi planner van dung thong ke cua
bang vai tram dong va chon ke hoach sai hoan toan. Moi so do sau do deu vo nghia.
