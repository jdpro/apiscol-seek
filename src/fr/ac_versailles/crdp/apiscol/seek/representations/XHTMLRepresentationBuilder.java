package fr.ac_versailles.crdp.apiscol.seek.representations;

import javax.ws.rs.core.MediaType;

public class XHTMLRepresentationBuilder extends XMLRepresentationBuilder {

	@Override
	public MediaType getMediaType() {
		return MediaType.TEXT_HTML_TYPE;
	}

}
