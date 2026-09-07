package vn.ptit.librax.borrowing.dto;

public record BorrowingDetailResponse(
        Long borrowingId,
        Long bookId,
        String bookTitle,
        Long memberId,
        String memberName,
        String status
) {
}
