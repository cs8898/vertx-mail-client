package io.vertx.ext.mail;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.StringContains.containsString;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;
import io.vertx.test.core.VertxTestBase;

import java.util.concurrent.CountDownLatch;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.subethamail.wiser.Wiser;
import org.subethamail.wiser.WiserMessage;

/**
 * @author <a href="http://oss.lehmann.cx/">Alexander Lehmann</a>
 *
 * this test uses a local smtp server mockup
 */
public class MailLocalTest extends VertxTestBase {

  Vertx vertx = Vertx.vertx();
  private static final Logger log = LoggerFactory.getLogger(MailLocalTest.class);

  CountDownLatch latch;

  @Test
  public void mailTest() throws MessagingException, InterruptedException {
    log.info("starting");

    latch = new CountDownLatch(1);

    MailConfig mailConfig = new MailConfig("localhost", 1587, StarttlsOption.DISABLED, LoginOption.REQUIRED);

    mailConfig.setUsername("username");
    mailConfig.setPassword("asdf");

    MailService mailService = MailService.create(vertx, mailConfig);

    JsonObject email = new JsonObject();
    email.put("from", "lehmann333@arcor.de");
    email.put("recipients", new JsonArray().add("lehmann333@arcor.de (User Name)").add("user@example.com (Another User)"));
    email.put("bounceAddress", "user@example.com");
    email.put("subject", "Test email with HTML");
    email.put("text", "this is a test email");

    mailService.sendMail(email, result -> {
      log.info("mail finished");
      if(result.succeeded()) {
        log.info(result.result().toString());
        latch.countDown();
      } else {
        log.warn("got exception", result.cause());
        throw new RuntimeException(result.cause());
      }
    });

    awaitLatch(latch);

    final WiserMessage message = wiser.getMessages().get(0);
    String sender = message.getEnvelopeSender();
    final MimeMessage mimeMessage = message.getMimeMessage();
    assertEquals("user@example.com", sender);
    assertThat(mimeMessage.getContentType(), containsString("text/plain"));
    assertThat(mimeMessage.getSubject() , equalTo("Test email with HTML"));
  }

  Wiser wiser;

  @Before
  public void startSMTP() {
    wiser = new Wiser();
    wiser.setPort(1587);
    wiser.start();
  }

  @After
  public void stopSMTP() {
    if (wiser != null) {
      wiser.stop();
    }
  }
}
