package vn.ptit.librax.borrowing.service;

import java.util.Optional;

public interface BorrowingRepository {

    Optional<Borrowing> findById(Long borrowingId);
}
