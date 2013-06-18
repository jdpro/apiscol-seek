package fr.ac_versailles.crdp.apiscol.seek.representations;

import javax.ws.rs.core.MediaType;

public class JSonpReprensationBuilder extends XMLRepresentationBuilder {
	@Override
	public MediaType getMediaType() {
		return MediaType.APPLICATION_JSON_TYPE;
	}
	
}
