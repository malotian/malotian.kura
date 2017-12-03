package malotian.kura;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.kaa.schema.config.KaaConfiguration;
import org.kaa.schema.log.DHTRecord;
import org.kaaproject.kaa.client.DesktopKaaPlatformContext;
import org.kaaproject.kaa.client.Kaa;
import org.kaaproject.kaa.client.KaaClient;
import org.kaaproject.kaa.client.SimpleKaaClientStateListener;
import org.kaaproject.kaa.client.configuration.base.SimpleConfigurationStorage;
import org.kaaproject.kaa.client.logging.strategies.RecordCountLogUploadStrategy;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import malotian.kura.dht.kaa.client.DHTSensor;

public class ServiceActivator {

	static final Logger LOGGER = LoggerFactory.getLogger(ServiceActivator.class);
	private KaaClient kaaClient;
	private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
	private ScheduledFuture<?> scheduledFuture;

	public ServiceActivator() {
		super();
	}

	protected void activate(final ComponentContext componentContext) {
		final DesktopKaaPlatformContext desktopKaaPlatformContext = new DesktopKaaPlatformContext();

		kaaClient = Kaa.newClient(desktopKaaPlatformContext, new SimpleKaaClientStateListener() {
			@Override
			public void onStarted() {
				super.onStarted();
				onKaaClientStarted();
			}

			@Override
			public void onStopped() {
				super.onStopped();
				onKaaClientStopped();
			}
		}, true);

		final RecordCountLogUploadStrategy strategy = new RecordCountLogUploadStrategy(1);
		strategy.setMaxParallelUploads(1);
		kaaClient.setLogUploadStrategy(strategy);

		kaaClient.setConfigurationStorage(new SimpleConfigurationStorage(desktopKaaPlatformContext, "saved_config.cfg"));
		kaaClient.addConfigurationListener(kaaConfiguration -> onChangedConfiguration(kaaConfiguration));

		kaaClient.start();

	}

	private void configure(final KaaConfiguration kaaConfiguration) {

		if (kaaConfiguration.getSamplingRate() <= 0) {
			LOGGER.error("wrong time is used. please, check your configuration!");
			kaaClient.stop();
		}

		final DHTSensor dht11 = new DHTSensor(kaaConfiguration.getSensorType(), kaaConfiguration.getGPIOPin());
		dht11.setSimulationMode(kaaConfiguration.getSimulationMode());

		scheduledFuture = scheduledExecutorService.scheduleAtFixedRate(() -> {
			dht11.read();
			LOGGER.info("Temprature: {}*C, Humidity: {}%, Timestamp: {}", dht11.getTemprature(), dht11.getHumidity(), dht11.getTimestamp());
			kaaClient.addLogRecord(new DHTRecord(dht11.getTemprature(), dht11.getHumidity(), dht11.getTimestamp().getTime()));
		}, 0, kaaConfiguration.getSamplingRate(), TimeUnit.SECONDS);
	}

	protected void deactivate(final ComponentContext componentContext) {

		if (null != scheduledFuture) {
			scheduledFuture.cancel(false);
		}

		if (null != scheduledExecutorService) {
			scheduledExecutorService.shutdown();
		}

		kaaClient.stop();

	}

	private void onChangedConfiguration(final KaaConfiguration kaaConfiguration) {
		LOGGER.info("onChangedConfiguration, kaaConfiguration: {}", kaaConfiguration);

		if (null != scheduledFuture) {
			scheduledFuture.cancel(true);
		}

		configure(kaaConfiguration);
	}

	private void onKaaClientStarted() {
		final KaaConfiguration kaaConfiguration = kaaClient.getConfiguration();
		LOGGER.info("onKaaClientStarted, kaaConfiguration: {}", kaaConfiguration);
		configure(kaaConfiguration);
	}

	private void onKaaClientStopped() {
		LOGGER.info("onKaaClientStopped");
	}
}