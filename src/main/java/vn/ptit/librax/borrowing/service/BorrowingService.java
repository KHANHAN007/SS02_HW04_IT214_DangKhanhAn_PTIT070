package vn.ptit.librax.borrowing.service;

import org.springframework.stereotype.Service;
import vn.ptit.librax.borrowing.client.BookClient;
import vn.ptit.librax.borrowing.client.MemberClient;
import vn.ptit.librax.borrowing.dto.BookResponse;
import vn.ptit.librax.borrowing.dto.BorrowingDetailResponse;
import vn.ptit.librax.borrowing.dto.MemberResponse;

@Service
public class BorrowingService {

    private final BorrowingRepository borrowingRepository;
    private final BookClient bookClient;
    private final MemberClient memberClient;

    public BorrowingService(BorrowingRepository borrowingRepository,
                            BookClient bookClient,
                            MemberClient memberClient) {
        this.borrowingRepository = borrowingRepository;
        this.bookClient = bookClient;
        this.memberClient = memberClient;
    }

    public BorrowingDetailResponse getBorrowingDetail(Long borrowingId) {
        Borrowing borrowing = borrowingRepository.findById(borrowingId)
                .orElseThrow(() -> new IllegalArgumentException("Borrowing not found"));

        BookResponse book = bookClient.getBookById(borrowing.getBookId());
        MemberResponse member = memberClient.getMemberById(borrowing.getMemberId());

        return new BorrowingDetailResponse(
                borrowing.getId(),
                book.id(),
                book.title(),
                member.id(),
                member.name(),
                borrowing.getStatus()
        );
    }
}
