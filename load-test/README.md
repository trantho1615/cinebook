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

## Do thong luong

`throughput.js` tra loi mot cau khac han `flash-sale.js`: **day den bao nhieu request/giay
thi he thong gay, va gay o dau.**

Khac biet ky thuat quyet dinh la executor:

| | `flash-sale.js` | `throughput.js` |
|---|---|---|
| Executor | `constant-vus` | `constant-arrival-rate` |
| `sleep()` | co, 1-2 giay | khong |
| Tai do ai quyet dinh | kich ban (50 VU x nghi 2s = ~26 req/s) | ta chi dinh, k6 giu dung nhip |
| Tra loi cau | tranh chap thi ai thang | day den dau thi gay |

Voi `constant-vus`, server cham lai thi tai TU DONG giam theo — nen khong bao gio thay
duoc he thong that su duoi o dau. Do la ly do con so 26,3 req/s trong bao cao cu **khong
phai gioi han he thong**.

### Chay

Can du lieu lon (`seed-large.sql`) va danh sach suat chieu:

```bash
docker exec cinebook-postgres psql -U cinebook -d cinebook -tAc \
  "SELECT json_agg(id)::text FROM (SELECT id FROM seed_ref_showtimes ORDER BY rn LIMIT 5000) t;" \
  > load-test/showtime-ids.json

MSYS_NO_PATHCONV=1 docker run --rm -i --add-host=host.docker.internal:host-gateway \
  -v "$PWD/load-test:/scripts" grafana/k6:1.5.0 run /scripts/throughput.js
```

Chay **khong co worker**: outbox relay se co day event chua publish len Kafka va lam nhieu
phep do.

### Hai cho de tu lua minh

**`dropped_iterations` khac 0 nghia la k6 khong sinh du tai.** Khi moi iteration keo dai
qua lau, k6 cham tran `maxVUs` va bo bot luot. Con so RPS "dat duoc" van la thong luong
that cua he thong, nhung khong duoc doc no nhu "he thong chi chiu duoc chung nay khi bi de
dung muc tieu". Bang tong ket in cot nay ra co y.

**`summaryTrendStats` phai duoc khai bao.** Mac dinh k6 chi tinh avg/min/med/max/p(90)/
p(95). Doc `values['p(99)']` khi chua khai thi duoc `undefined`, va `handleSummary` nem
"Cannot read property 'toFixed' of undefined" — k6 nuot loi do va chi in bang mac dinh,
nen rat de tuong minh dang doc so lieu day du. Lan chay dau tien da dinh dung loi nay.
