package in.bushansirgur.billingsoftware;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.s3.S3Client;

@SpringBootTest
@ActiveProfiles("test")
class BillingsoftwareApplicationTests {

	@MockBean
	private S3Client s3Client;

	@Test
	void contextLoads() {
	}

}
