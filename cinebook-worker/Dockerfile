# syntax=docker/dockerfile:1

# ---- Giai doan 1: build ----
# Dung image co Maven de nguoi chay khong phai cai Java hay Maven tren may.
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /src

# Copy pom truoc, tai dependency truoc, roi moi copy ma nguon: doi mot dong code thi Docker
# van dung lai lop dependency da cache thay vi tai lai tu dau.
COPY pom.xml .
COPY common/pom.xml common/
COPY cinebook-api/pom.xml cinebook-api/
COPY cinebook-worker/pom.xml cinebook-worker/
RUN mvn -B -q dependency:go-offline -DskipTests || true

COPY . .
# install chu khong package: fat jar cua worker goi cinebook-api lay tu kho cuc bo, nen
# package don thuan co the dong goi mot ban api cu.
RUN mvn -B -q -DskipTests clean install

# ---- Giai doan 2: tach lop ----
FROM eclipse-temurin:25-jre AS layers
WORKDIR /app
COPY --from=build /src/cinebook-worker/target/cinebook-worker-*.jar app.jar
# Boot 4 dung jarmode=tools. Cach cu -Djarmode=layertools bao "Unsupported jarmode".
#
# Worker om ca cinebook-api ben trong duoi dang SNAPSHOT dependency, nen phan do roi vao lop
# snapshot-dependencies — doi theo moi lan build nhung nho, dung cho no can.
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# ---- Giai doan 3: image chay ----
FROM eclipse-temurin:25-jre
WORKDIR /app

# Thu tu copy theo do ON DINH, khong phai theo thu tu logic:
#   dependencies      81 MB, gan nhu khong bao gio doi
#   spring-boot-loader 637 KB
#   snapshot-dependencies
#   application       932 KB, doi moi lan build
# Doi mot dong code thi chi lop cuoi bi day lai, khong phai 80 MB.
COPY --from=layers /app/extracted/dependencies/ ./
COPY --from=layers /app/extracted/spring-boot-loader/ ./
COPY --from=layers /app/extracted/snapshot-dependencies/ ./
COPY --from=layers /app/extracted/application/ ./

# curl cho healthcheck cua compose. Image eclipse-temurin:25-jre KHONG co san curl lan
# wget — healthcheck se bao "curl: not found" va container bi danh dau unhealthy trong khi
# ung dung chay hoan toan binh thuong. Da gap dung tinh huong do.
RUN apt-get update     && apt-get install -y --no-install-recommends curl     && rm -rf /var/lib/apt/lists/*

# Khong chay bang root.
RUN useradd --system --uid 1001 cinebook && chown -R cinebook:cinebook /app
USER cinebook

# 8081 nghiep vu, 8091 quan tri (metric va health)
EXPOSE 8081 8091

# Ten lop launcher da doi o Boot 3 (them ".launch"). Da doc tu MANIFEST cua chinh jar nay
# chu khong chep tu blog.
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
