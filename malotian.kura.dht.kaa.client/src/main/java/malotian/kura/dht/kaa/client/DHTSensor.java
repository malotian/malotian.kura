package malotian.kura.dht.kaa.client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.MessageFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.RandomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DHTSensor {

	static final Logger LOGGER = LoggerFactory.getLogger(DHTSensor.class);

	public static void main(final String[] args) {
		final DHTSensor dht = new DHTSensor(11, 4);

		for (int i = 0; i < 5; i++) {
			try {
				Thread.sleep(20);
				dht.read();
				System.out.println("T: " + dht.temprature);
				System.out.println("H: " + dht.humidity);
			} catch (final Exception e) {
				e.printStackTrace();
			}

		}

		System.out.println("Done!!");

	}

	float humidity = -1;

	int pin;

	int sensorType;

	private boolean simulationMode;

	float temprature = -1;

	Date timestamp = null;

	public DHTSensor(final int sensorType, final int pin) {
		super();
		this.sensorType = sensorType;
		this.pin = pin;
	}

	public float getHumidity() {
		return humidity;
	}

	public float getTemprature() {
		return temprature;
	}

	public Date getTimestamp() {
		return timestamp;
	}

	public boolean isSimulationModeON() {
		return simulationMode;
	}

	public boolean read() {
		try {
			return readUnsafe();
		} catch (final Exception e) {
			return false;
		}
	}

	public boolean readUnsafe() throws Exception {

		if (isSimulationModeON()) {
			LOGGER.info("sensor mode: simulation");
			timestamp = new Date();
			temprature = RandomUtils.nextInt(1, 1000) % 45;
			humidity = RandomUtils.nextInt(1, 1000) % 25;
			return true; // dummy data
		}

		final Process p = Runtime.getRuntime().exec(MessageFormat.format("sudo python /home/pi/Adafruit_Python_DHT/examples/AdafruitDHT.py {0} {1}", sensorType, pin).split(" "));
		p.waitFor();
		final BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));

		String output = "", line;
		while ((line = input.readLine()) != null) {
			if (!output.isEmpty()) {
				output += '\n';
			}
			output += line;
		}

		LOGGER.info("DHT11 ret: {}", output.trim());
		if (output.length() == 0) {
			throw new RuntimeException("/home/pi/Adafruit_Python_DHT/examples/AdafruitDHT.py not present");
		}
		if (output.contains("error") || !output.contains("Temp") || !output.contains("Humidity")) {
			throw new Exception("error");
		}

		final Pattern pattern = Pattern.compile("([-+]?[0-9]*\\.?[0-9]+)");
		final Matcher matcher = pattern.matcher(output);

		matcher.find();
		final float t = Float.parseFloat(matcher.group());

		matcher.find();
		final float h = Float.parseFloat(matcher.group());

		if (t < 18) {
			return false;// just ignore it
		}

		if (t != temprature || h != humidity) {
			timestamp = new Date();
			temprature = t;
			humidity = h;
		}

		return true;

	}

	public void setSimulationMode(boolean simulate) {
		simulationMode = simulate;
	}
}
