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
