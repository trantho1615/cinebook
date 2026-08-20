package com.cinebook.shared.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Lay bi mat tu cau hinh, hoac sinh mot bi mat ngau nhien cho lan chay nay.
 *
 * Vi sao lop nay ton tai: truoc day application.yml mang gia tri mac dinh CHAY DUOC
 *   secret: ${CINEBOOK_JWT_SECRET:doi-chuoi-nay-truoc-khi-deploy-that-va-dai-toi-thieu-32-byte}
 * Ba van de voi cach do:
 *   1. Deploy quen dat bien moi truong thi he thong van len, va bat ky ai doc repo cung ky
 *      duoc token gia cho bat ky tai khoan nao.
 *   2. Cong cu quet bi mat (GitGuardian) bao dong — dung ly do.
 *   3. Khong co gi nhac nguoi chay rang ho dang dung mot gia tri ai cung biet.
 *
 * Cach thay the: khong co bi mat trong ma nguon nua. Thieu bien thi sinh ngau nhien cho lan
 * chay nay va CANH BAO TO — tien cho may lap trinh vien, nhung token khong song qua lan khoi
 * dong sau, nen khong ai vo tinh mang no ra that.
 *
 * Profile prod thi khac han: application-prod.yml khai ${CINEBOOK_JWT_SECRET} khong co gia
 * tri mac dinh, nen thieu bien la ung dung chet ngay. ProdConfigTest canh dieu do.
 */
public final class SecretResolver {

    private static final Logger log = LoggerFactory.getLogger(SecretResolver.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private SecretResolver() {
    }

    public static String hoacSinhNgauNhien(String giaTri, String tenBienMoiTruong) {
        if (giaTri != null && !giaTri.isBlank()) {
            return giaTri;
        }

        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        String biMatTam = Base64.getEncoder().encodeToString(bytes);

        log.warn("""

                =====================================================================
                {} chua duoc dat. Da sinh mot bi mat NGAU NHIEN cho lan chay nay.
                Moi token va chu ky se mat hieu luc khi khoi dong lai.
                Chi dung duoc khi phat trien. Moi truong that phai dat bien nay.
                =====================================================================
                """, tenBienMoiTruong);

        return biMatTam;
    }
}
