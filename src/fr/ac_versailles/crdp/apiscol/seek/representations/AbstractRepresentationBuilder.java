package fr.ac_versailles.crdp.apiscol.seek.representations;

import org.apache.log4j.Logger;

import fr.ac_versailles.crdp.apiscol.utils.LogUtility;

public abstract class AbstractRepresentationBuilder implements
		IEntitiesRepresentationBuilder {
	protected static Logger logger;

	public AbstractRepresentationBuilder() {
		createLogger();
	}

	private void createLogger() {
		if (logger == null)
			logger = LogUtility
					.createLogger(this.getClass().getCanonicalName());

	}
}
