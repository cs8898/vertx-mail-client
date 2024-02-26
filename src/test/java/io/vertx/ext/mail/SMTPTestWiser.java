/*
 *  Copyright (c) 2011-2015 The original author or authors
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *       The Eclipse Public License is available at
 *       http://www.eclipse.org/legal/epl-v10.html
 *
 *       The Apache License v2.0 is available at
 *       http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.ext.mail;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.mail.internet.MimeMessage;

import org.subethamail.smtp.*;
import org.subethamail.wiser.Wiser;
import org.subethamail.wiser.WiserMessage;

import static org.hamcrest.core.StringContains.containsString;

import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;

/**
 * Start/stop a dummy test server for each test
 * <p>
 * the server is Wiser, this supports inspecting the messages that were received in the test code
 *
 * @author <a href="http://oss.lehmann.cx/">Alexander Lehmann</a>
 */
public class SMTPTestWiser extends SMTPTestBase {

  private static final Logger log = LoggerFactory.getLogger(SMTPTestWiser.class);

  protected Wiser wiser;


  protected void startSMTP(String factory) {
    startSMTP(factory, false);
  }
  protected void startSMTP(String factory, boolean bareLf) {
    wiser = new Wiser();

    wiser.setPort(1587);
    wiser.getServer().setAuthenticationHandlerFactory(new AuthenticationHandlerFactory() {
      /*
       * AUTH PLAIN handler which returns success on any string
       */
      @Override
      public List<String> getAuthenticationMechanisms() {
        return Arrays.asList("PLAIN");
      }

      @Override
      public AuthenticationHandler create() {
        return new AuthenticationHandler() {

          @Override
          public String auth(final String clientInput) throws RejectException {
            log.info(clientInput);
            return null;
          }

          @Override
          public Object getIdentity() {
            return "username";
          }
        };
      }
    });

    Security.setProperty("ssl.SocketFactory.provider", factory);

    if(bareLf) {
      MessageHandlerFactory originalFactory = wiser.getServer().getMessageHandlerFactory();
      wiser.getServer().setMessageHandlerFactory(ctx -> {
        final MessageHandler originalHandler = originalFactory.create(ctx);
        return new MessageHandler() {
          @Override
          public void from(String from) throws RejectException {
            originalHandler.from(from);
          }

          @Override
          public void recipient(String recipient) throws RejectException {
            originalHandler.recipient(recipient);
          }

          @Override
          public void data(InputStream data) throws RejectException, TooMuchDataException, IOException {
            String dataString = TestUtils.inputStreamToString(data);

            List<Integer> bareLfIndexes = new ArrayList<>();
            for (int i = 0; i < dataString.length(); i++) {
              if (dataString.charAt(i) == '\n') {
                if (i == 0 || dataString.charAt(i - 1) != '\r') {
                  bareLfIndexes.add(i);
                }
              }
            }

            if (!bareLfIndexes.isEmpty()) {
              throw new RejectException(String.format("bare <LF> received after DATA %s", bareLfIndexes));
            }

            InputStream stream = new ByteArrayInputStream(dataString.getBytes(StandardCharsets.UTF_8));
            originalHandler.data(stream);
          }

          @Override
          public void done() {
            originalHandler.done();
          }
        };
      });
    }

    wiser.getServer().setEnableTLS(true);

    wiser.start();
  }

  /*
   * use default certificate
   */
  protected void startSMTP() {
    startSMTP(KeyStoreSSLSocketFactory.class.getName());
  }

  protected void stopSMTP() {
    if (wiser != null) {
      wiser.stop();
    }
  }

  protected AdditionalAsserts assertExampleMessage() {
    return () -> {
      final WiserMessage message = wiser.getMessages().get(0);
      testContext.assertEquals("from@example.com", message.getEnvelopeSender());
      final MimeMessage mimeMessage = message.getMimeMessage();
      assertThat(mimeMessage.getContentType(), containsString("text/plain"));
      testContext.assertEquals("Subject", mimeMessage.getSubject());
      testContext.assertEquals("Message\n", TestUtils.conv2nl(TestUtils.inputStreamToString(mimeMessage.getInputStream())));
    };
  }

}
