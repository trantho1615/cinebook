package com.cinebook.identity.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Be mat doc thong tin nguoi dung cho module khac.
 *
 * notification can dia chi email de gui ve, nhung khong duoc doc thang bang users —
 * bang do thuoc quyen so huu cua identity (spec muc 3).
 */
public interface UserLookup {

    Optional<String> findEmail(UUID userId);
}
