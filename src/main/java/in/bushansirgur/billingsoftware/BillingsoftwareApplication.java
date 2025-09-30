package in.bushansirgur.billingsoftware;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BillingsoftwareApplication {

    public static void main(String[] args) {
        SpringApplication.run(BillingsoftwareApplication.class, args);
    }

}
