package vn.ptit.librax.borrowing.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import vn.ptit.librax.borrowing.dto.BookResponse;

@Component
public class BookClient {

    private final RestTemplate restTemplate;
    private final String bookServiceBaseUrl;

    public BookClient(RestTemplate restTemplate,
                      @Value("${book-service.base-url}") String bookServiceBaseUrl) {
        this.restTemplate = restTemplate;
        this.bookServiceBaseUrl = bookServiceBaseUrl;
    }

    public BookResponse getBookById(Long bookId) {
        return restTemplate.getForObject(
                bookServiceBaseUrl + "/api/books/{bookId}",
                BookResponse.class,
                bookId
        );
    }
}
