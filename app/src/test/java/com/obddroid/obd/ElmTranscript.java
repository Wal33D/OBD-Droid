package com.obddroid.obd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.obddroid.interfaces.TelegramListener;
import com.obddroid.interfaces.TelegramWriter;

import java.beans.PropertyChangeEvent;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Replays a recorded ELM327 session against a real {@link ElmProt}, with no adapter, phone or car.
 *
 * <p>Transcripts live in test resources under {@code /elm/}. One entry per line:
 * <pre>
 *   # comment                 ignored, as are blank lines
 *   TX 0100                   the app must have sent exactly this, in this order
 *   RX 41 00 BE 1F B8 13      the adapter prints this line (followed by CR)
 *   &gt;                         the adapter prints its prompt
 *   &#64;reset                    call elm.reset() (sends ATZ, or ATI when ATZ is disabled)
 *   &#64;service 01               call elm.setService(0x01)
 *   &#64;status ECU_DETECTED      assert elm.getStatus()
 *   &#64;include other.txt        replay another transcript from /elm/ in place
 * </pre>
 * Adapter output goes through the app's own {@link StreamHandler}, so space stripping and CR/prompt
 * framing are exactly what the app does on a real link. Every TX must be listed before the next
 * RX line, and nothing may be sent that the transcript does not list.
 */
final class ElmTranscript
{
	final ElmProt elm = new ElmProt();
	/** every telegram the app sent, in order */
	final List<String> sent = new ArrayList<>();
	/** every property change the protocol fired, in order */
	final List<PropertyChangeEvent> events = new ArrayList<>();

	private final StreamHandler stream = new StreamHandler();
	private int txChecked = 0;
	private RuntimeException handlerFailure;
	private String where = "";

	ElmTranscript()
	{
		elm.addTelegramWriter(new TelegramWriter()
		{
			@Override
			public int writeTelegram(char[] buffer)
			{
				return writeTelegram(buffer, 0, null);
			}

			@Override
			public int writeTelegram(char[] buffer, int type, Object id)
			{
				sent.add(new String(buffer));
				return buffer.length;
			}
		});
		elm.addPropertyChangeListener(events::add);
		// StreamHandler swallows handler exceptions; keep them so a broken parser fails the test
		stream.setMessageHandler(new TelegramListener()
		{
			@Override
			public int handleTelegram(char[] buffer)
			{
				try
				{
					return elm.handleTelegram(buffer);
				}
				catch (RuntimeException e)
				{
					if (handlerFailure == null) { handlerFailure = e; }
					throw e;
				}
			}
		});
	}

	/** Reset the static protocol state that ObdProt/ElmProt share between instances. */
	static void resetSharedState()
	{
		ObdProt.cmdQueue.clear();
		ObdProt.lastTxMsg = "";
		ObdProt.lastRxMsg = "";
		ObdProt.pidsWrapped = false;
		ElmProt.disableCommands(null);
		ElmProt.setPreferredProtocol(ElmProt.PROT.ELM_PROT_AUTO.ordinal());
	}

	/** Replay a transcript from test resources (/elm/&lt;resource&gt;). */
	ElmTranscript play(String resource) throws IOException
	{
		InputStream in = ElmTranscript.class.getResourceAsStream("/elm/" + resource);
		assertNotNull("transcript not found: /elm/" + resource, in);
		return play(resource, new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
	}

	/** Replay transcript lines given inline (same format as the resource files). */
	ElmTranscript playText(String name, String text) throws IOException
	{
		return play(name, new BufferedReader(new java.io.StringReader(text)));
	}

	private ElmTranscript play(String resource, BufferedReader reader) throws IOException
	{
		try (BufferedReader rdr = reader)
		{
			String line;
			int lineNo = 0;
			while ((line = rdr.readLine()) != null)
			{
				lineNo++;
				String outer = where;
				where = resource + ":" + lineNo;
				step(line.trim());
				where = outer;
			}
		}
		return this;
	}

	/** Assert that everything the app sent was listed in the transcript. */
	void assertNoUnexpectedTx()
	{
		if (txChecked < sent.size())
		{
			fail("app sent " + sent.subList(txChecked, sent.size()) + " not listed in the transcript");
		}
	}

	private void step(String line)
	{
		if (line.isEmpty() || line.startsWith("#")) { return; }

		if (line.startsWith("TX "))
		{
			String expected = line.substring(3).trim();
			assertTrue(where + ": expected TX '" + expected + "' but the app sent nothing more (sent: " + sent + ")",
				txChecked < sent.size());
			assertEquals(where + ": TX #" + txChecked, expected, sent.get(txChecked));
			txChecked++;
		}
		else if (line.equals(">"))
		{
			feed(">");
		}
		else if (line.startsWith("RX ") || line.equals("RX"))
		{
			feed(line.substring(2).trim() + "\r");
		}
		else if (line.equals("@reset"))
		{
			elm.reset();
		}
		else if (line.startsWith("@service "))
		{
			elm.setService(Integer.parseInt(line.substring(9).trim(), 16));
		}
		else if (line.startsWith("@status "))
		{
			assertEquals(where + ": status", ElmProt.STAT.valueOf(line.substring(8).trim()), elm.getStatus());
		}
		else if (line.startsWith("@include "))
		{
			try
			{
				play(line.substring(9).trim());
			}
			catch (IOException e)
			{
				throw new AssertionError(where + ": " + e, e);
			}
		}
		else
		{
			fail(where + ": cannot parse transcript line '" + line + "'");
		}
	}

	private void feed(String adapterOutput)
	{
		if (txChecked < sent.size())
		{
			fail(where + ": app sent " + sent.subList(txChecked, sent.size())
			     + " before this adapter output; list it with TX first");
		}
		final byte[] bytes = adapterOutput.getBytes(StandardCharsets.US_ASCII);
		stream.setStreams(new InputStream()
		{
			private int pos = 0;

			@Override
			public int read()
			{
				return pos < bytes.length ? bytes[pos++] : -1;
			}

			@Override
			public int available()
			{
				// never 0: StreamHandler.run() would poll forever; read() returns -1 to end the run
				return Math.max(1, bytes.length - pos);
			}
		}, new ByteArrayOutputStream());
		stream.run();
		if (handlerFailure != null)
		{
			RuntimeException e = handlerFailure;
			handlerFailure = null;
			throw new AssertionError(where + ": ElmProt.handleTelegram threw " + e, e);
		}
	}
}
