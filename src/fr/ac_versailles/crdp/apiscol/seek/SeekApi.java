package fr.ac_versailles.crdp.apiscol.seek;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.lang.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import com.google.gson.Gson;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.json.JSONWithPadding;
import com.sun.jersey.core.util.MultivaluedMapImpl;

import fr.ac_versailles.crdp.apiscol.ApiscolApi;
import fr.ac_versailles.crdp.apiscol.CustomMediaType;
import fr.ac_versailles.crdp.apiscol.ParametersKeys;
import fr.ac_versailles.crdp.apiscol.utils.JSonUtils;

@Path("/")
public class SeekApi extends ApiscolApi {

	@Context
	UriInfo uriInfo;
	@Context
	ServletContext context;

	private static boolean staticInitialization = false;
	private static Client client;
	private static WebResource contentWebServiceResource;
	private static WebResource metadataWebServiceResource;
	private static WebResource thumbsWebServiceResource;
	private static WebResource packWebServiceResource;
	private static HashMap<UUID, Document> requestWorkersResponses;

	public SeekApi(@Context ServletContext context) {
		super(context);
		if (!staticInitialization) {
			initializeStaticParameters(context);
			createWebServiceClients(context);
			staticInitialization = true;
		}
	}

	private void createWebServiceClients(ServletContext context) {
		client = Client.create();

		URI contentWebserviceUrl = null;
		URI metadataWebserviceUrl = null;
		URI thumbsWebserviceUrl = null;
		URI packWebserviceUrl = null;
		try {
			contentWebserviceUrl = new URI(getProperty(
					ParametersKeys.contentWebserviceUrl, context));
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			metadataWebserviceUrl = new URI(getProperty(
					ParametersKeys.metadataWebserviceUrl, context));
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			packWebserviceUrl = new URI(getProperty(
					ParametersKeys.packWebserviceUrl, context));
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			thumbsWebserviceUrl = new URI(getProperty(
					ParametersKeys.thumbsWebserviceUrl, context));
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
		client.setConnectTimeout(3000);
		contentWebServiceResource = client.resource(UriBuilder.fromUri(
				contentWebserviceUrl).build());
		metadataWebServiceResource = client.resource(UriBuilder.fromUri(
				metadataWebserviceUrl).build());
		packWebServiceResource = client.resource(UriBuilder.fromUri(
				packWebserviceUrl).build());
		thumbsWebServiceResource = client.resource(UriBuilder.fromUri(
				thumbsWebserviceUrl).build());

	}

	private void initializeStaticParameters(ServletContext context) {
		requestWorkersResponses = new HashMap<UUID, Document>();
	}

	/**
	 * @param metadataId
	 *            The <code>'mdid'</code> query parameter. If this parameter is
	 *            provided, any other query or filtering parameter will be
	 *            ignored. It is a way to directly request an entry in the
	 *            metadata database for a client who would only know the seek
	 *            service URL.
	 * @param query
	 *            The <code>'query'</code> query parameter. Full text search
	 *            query send to Solr.
	 * @param callBack
	 *            The <code>'callback'</code> query parameter. For
	 *            application/javascript request type, client can specify the
	 *            JavaScript function in which the response will be wrapped.
	 * @param fuzzy
	 *            This enables fuzzy search in Solr. Attention! Fuzzy search
	 *            disables all other treatments on strings and can cause
	 *            confusing results.
	 * @param staticFilters
	 *            A Json style list of strings.<br/>
	 *            Each one is structured following the pattern : element::value<br/>
	 *            example :
	 *            ["educational.place::en atelier","educational.tool::TBI"]
	 * @param dynamicFilters
	 *            A Json style list of strings.<br/>
	 *            Each one is structured following the pattern :
	 *            classification.taxonPath.purpose::source::id::entry <br/>
	 *            example : ["discipline::Diplômes::40022106::BAC PRO Cuisine",
	 *            "discipline::Nomenclature disciplines professionnelle::HRT::Hotellerie restauration tourisme"
	 *            ]
	 * @param start
	 *            Pagination start
	 * @param rows
	 *            Pagination end
	 * @param includeDescription
	 *            If set to true, more informative (textual) content will be
	 *            delivered : title, summary. For clients who are considering
	 *            requesting the whole scolomfr document, it is useless.
	 * @return The ATOM representation of the metadata list with additional
	 *         information concerning the query matches, spellcheck suggestions,
	 *         etc. The content of <code>'entry'</code> tag is the metadata ATOM
	 *         representation. Static and dynamic facets matches the structure
	 *         of scoLOMfr metadata. Dynamic Facets are intended to apply
	 *         filters coming from the <code>'classification'</code> element.
	 *         Notice that if a full text search excerpt comes from ApiScol
	 *         Content, this is notified by the attribute :
	 *         <code>source="data"</code> in <code>apiscol:match</code> tag.
	 *         <code>
	 * &lt;feed xmlns="http://www.w3.org/2005/Atom" xmlns:apiscol="http://www.crdp.ac-versailles.fr/2012/apiscol"&gt;
	 * &nbsp;&nbsp;&nbsp;&lt;link
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;href="http://server.url:server.port/meta/?query=statistiques&format=xml&desc=true"
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;rel="self" /&gt;
	 * &nbsp;&nbsp;&nbsp;&lt;logo&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;http://apiscol.crdp-versailles.fr/cdn/0.0.1/img/logo-api.png
	 * &nbsp;&nbsp;&nbsp;&lt;/logo&gt;
	 * &nbsp;&nbsp;&nbsp;&lt;icon&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;http://apiscol.crdp-versailles.fr/cdn/0.0.1/img/logo-api.png
	 * &nbsp;&nbsp;&nbsp;&lt;/icon&gt;
	 * &nbsp;&nbsp;&nbsp;&lt;id&gt;http://server.url:server.port/meta/&lt;/id&gt;
	 * &nbsp;&nbsp;&nbsp;&lt;title&gt;Example de dépôt de ressources - eclipse&lt;/title&gt;
	 * &nbsp;&nbsp;&nbsp;&lt;generator&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;ApiScol, Dépôt de ressources pédagogiques - CRDP de l'Académie de Versailles
	 * &nbsp;&nbsp;&nbsp;&lt;/generator&gt;
	 * &nbsp;&nbsp;&nbsp;&lt;updated&gt;2013-03-13T15:05:02.000+01:00&lt;/updated&gt;
	 * &nbsp;&nbsp;&nbsp;&lt;apiscol:length&gt;2&lt;/apiscol:length&gt;
	 * &nbsp;&nbsp;&nbsp;&lt;entry&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;updated&gt;2013-03-13T15:05:02.000+01:00&lt;/updated&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:score&gt;0.7545044&lt;/apiscol:score&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;id&gt;
	 * &nbsp;&nbsp;&nbsp;urn:apiscol:example-dev:meta:metadata:cf963387-1652-4f7d-a8e2-64b516b876e3
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;/id&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;title&gt;Méthodologie de l'enquête statistique&lt;/title&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;summary&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Introduction générale à l'enquête statistique. La démarche expérimentale en
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;sciences sociales : "Traiter les faits sociaux comme des choses". .
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;/summary&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;link
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;href="http://server.url:server.port/thumbs/files/a/b/f/d5907857c7fff06b3ad252aa146d1.jpg"
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;rel="icon" type="image/jpeg" /&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;content src="http://www.sciences.sociales.fr/url-of-the-content" type="text/html" /&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;author&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;name&gt;Dupont, Victor&lt;/name&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;/author&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;link
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;href="http://server.url:server.port/meta/cf963387-1652-4f7d-a8e2-64b516b876e3"
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;rel="self" type="text/html" /&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;link
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;href="http://server.url:server.port/meta/cf963387-1652-4f7d-a8e2-64b516b876e3?format=xml"
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;rel="self" type="application/atom+xml" /&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;link href="nullmeta/cf963387-1652-4f7d-a8e2-64b516b876e3" rel="edit"
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;type="application/atom+xml" /&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;link
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;href="http://server.url:server.port/meta/lom/c/f/9/63387-1652-4f7d-a8e2-64b516b876e3.xml"
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;rel="describedby" type="application/atom+xml" /&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;link
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;href="http://server.url:server.port/meta/lom/c/f/9/63387-1652-4f7d-a8e2-64b516b876e3.js"
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;rel="describedby" type="application/javascript" /&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:code-snippet
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;href="http://server.url:server.port/meta/cf963387-1652-4f7d-a8e2-64b516b876e3/snippet" /&gt;
	 * &nbsp;&nbsp;&nbsp;&lt;/entry&gt;
	 * &nbsp;&nbsp;&nbsp;&lt;apiscol:facets&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:static-facets name="lifecycle.status"&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:facet count="1"&gt;final&lt;/apiscol:facet&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;/apiscol:static-facets&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:static-facets name="rights.copyrightandotherrestrictions"&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:facet count="1"&gt;true&lt;/apiscol:facet&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;/apiscol:static-facets&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:static-facets name="rights.costs"&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:facet count="1"&gt;false&lt;/apiscol:facet&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;/apiscol:static-facets&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:static-facets name="educational.place"&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:facet count="1"&gt;en salle de classe&lt;/apiscol:facet&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;/apiscol:static-facets&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:static-facets name="educational.intendedenduserrole"&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:facet count="1"&gt;learner&lt;/apiscol:facet&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:facet count="1"&gt;teacher&lt;/apiscol:facet&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;/apiscol:static-facets&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:static-facets name="lifecycle.contributor.author"&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:facet count="1"&gt;Dornbusch, Joachim&lt;/apiscol:facet&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;/apiscol:static-facets&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:static-facets name="educational.learningresourcetype"&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:facet count="1"&gt;lecture&lt;/apiscol:facet&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;/apiscol:static-facets&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:static-facets name="educational.language"&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:facet count="1"&gt;fre&lt;/apiscol:facet&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;/apiscol:static-facets&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:static-facets name="educational.educationalmethod"&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:facet count="1"&gt;en classe entière&lt;/apiscol:facet&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;/apiscol:static-facets&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:static-facets name="educational.activity"&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:facet count="1"&gt;apprendre&lt;/apiscol:facet&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;/apiscol:static-facets&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:static-facets name="general.generalresourcetype"&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:facet count="1"&gt;diaporama&lt;/apiscol:facet&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;/apiscol:static-facets&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:static-facets name="technical.format"&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:facet count="1"&gt;text/html&lt;/apiscol:facet&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;/apiscol:static-facets&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:static-facets name="educational.context"&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:facet count="1"&gt;school&lt;/apiscol:facet&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:facet count="1"&gt;enseignement secondaire&lt;/apiscol:facet&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;/apiscol:static-facets&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:static-facets name="educational.tool"&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:facet count="1"&gt;TBI&lt;/apiscol:facet&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;/apiscol:static-facets&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:dynamic-facets name="educational_level"&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:taxon identifier="scolomfr-voc-022"&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:entry count="1" identifier="scolomfr-voc-022-num-027"
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;label="2de générale" /&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:entry count="1" identifier="scolomfr-voc-022-num-087"
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;label="lycée général" /&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;/apiscol:taxon&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;/apiscol:dynamic-facets&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:dynamic-facets name="public_cible_détaillé"&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:taxon identifier="scolomfr-voc-021"&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:entry count="1" identifier="scolomfr-voc-021-num-00092"
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;label="professeur de lycée" /&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;/apiscol:taxon&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;/apiscol:dynamic-facets&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:dynamic-facets name="enseignement" /&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:dynamic-facets name="competency" /&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:dynamic-facets name="discipline"&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:taxon identifier="Nomenclature disciplines générales"&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:entry count="1" identifier="SES"
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;label="Sciences économiques et sociales" /&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;/apiscol:taxon&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;/apiscol:dynamic-facets&gt;
	 * &nbsp;&nbsp;&nbsp;&lt;/apiscol:facets&gt;
	 * &nbsp;&nbsp;&nbsp;&lt;apiscol:hits&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:hit
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;metadataId="urn:apiscol:example-dev:meta:metadata:cf963387-1652-4f7d-a8e2-64b516b876e3"&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:matches&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:match&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;M&#233;thodologie de l'enqu&#234;te
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;b&gt;statistique&lt;/b&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;/apiscol:match&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;/apiscol:matches&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;/apiscol:hit&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:hit
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;metadataId="urn:apiscol:example-dev:meta:metadata:6858fcd2-88e2-48d8-8d21-5d854f60ac5a"&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:matches&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:match source="data"&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;M&#233;thodologie de l'enqu&#234;te
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;b&gt;statistique&lt;/b&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;/apiscol:match&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;/apiscol:matches&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;/apiscol:hit&gt;
	 * &nbsp;&nbsp;&nbsp;&lt;/apiscol:hits&gt;
	 * &nbsp;&nbsp;&nbsp;&lt;apiscol:spellcheck&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:query_term requested="statistiques"&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:word&gt;statistique&lt;/apiscol:word&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;/apiscol:query_term&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:queries&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;apiscol:query&gt;statistique&lt;/apiscol:query&gt;
	 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;/apiscol:queries&gt;
	 * &nbsp;&nbsp;&nbsp;&lt;/apiscol:spellcheck&gt;
	 * &lt;/feed&gt;
	 * </code>
	 * @throws UnknownMetadataRepositoryException
	 * @throws UniformInterfaceException
	 * @throws ClientHandlerException
	 * @throws MetadataRepositoryFailureException
	 */
	@GET
	@Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_ATOM_XML,
			MediaType.TEXT_HTML, MediaType.APPLICATION_XHTML_XML,
			"application/x-javascript" })
	public Response getMetadata(
			@Context HttpServletRequest request,
			@DefaultValue("") @QueryParam(value = "mdid") final String metadataId,
			@QueryParam(value = "query") final String query,
			@DefaultValue("handleQueryResult") @QueryParam(value = "callback") final String callBack,
			@DefaultValue("0") @QueryParam(value = "fuzzy") final float fuzzy,
			@DefaultValue("") @QueryParam(value = "static-filters") final String staticFilters,
			@DefaultValue("") @QueryParam(value = "dynamic-filters") final String dynamicFilters,
			@DefaultValue("0") @QueryParam(value = "start") final int start,
			@DefaultValue("10") @QueryParam(value = "rows") final int rows,
			@DefaultValue("false") @QueryParam(value = "thumbs") String addThumbs,
			@QueryParam(value = "format") final String format)
			throws UnknownMetadataRepositoryException,
			MetadataRepositoryFailureException, ClientHandlerException,
			UniformInterfaceException {
		String requestedFormat = guessRequestedFormat(request, format);
		if (StringUtils.isEmpty(metadataId))
			return searchMetadata(query, callBack, fuzzy, staticFilters,
					dynamicFilters, start, rows, requestedFormat,
					StringUtils.equals(addThumbs, "true"));

		else
			return getMetadataById(metadataId, callBack, requestedFormat,
					StringUtils.equals(addThumbs, "true"));

	}

	private Response getMetadataById(String metadataId, String callBack,
			String requestedFormat, boolean addThumbs)
			throws UnknownMetadataRepositoryException {
		// if metadataId is a fully qualified URL, cut the prefix
		addThumbs = false;
		String prefix = new StringBuilder()
				.append(metadataWebServiceResource.getURI()).append("/")
				.toString();
		if (!metadataId.startsWith(prefix)) {
			String message = "This seek instance does not handle search for this metadata repository "
					+ metadataId;
			logger.error(message);
			throw new UnknownMetadataRepositoryException(message);
		}

		MultivaluedMap<String, String> queryParams = new MultivaluedMapImpl();
		queryParams.add("desc", "true");
		queryParams.add("mdid", metadataId);
		ClientResponse metadataWebServiceResponse = metadataWebServiceResource
				.queryParams(queryParams)
				.accept(MediaType.APPLICATION_XML_TYPE)
				.get(ClientResponse.class);
		Document metadataResponse = null;
		if (metadataWebServiceResponse.getStatus() == Status.OK.getStatusCode())
			metadataResponse = metadataWebServiceResponse
					.getEntity(Document.class);
		else {
			// TODO lancer message d'erreur
			System.out.println(metadataWebServiceResponse
					.getEntity(String.class));
		}
		String metadataUri = new StringBuilder()
				.append(metadataWebServiceResource.getURI().toString())
				.append("/").append(metadataId).toString();
		// If addThumbs, send an additional request to thumbs web service.
		// Metadata identifier are allways sent as a Json list
		if (addThumbs) {
			List<String> metadataList = new ArrayList<String>();

			metadataList.add(metadataUri);
			String jsonMetadataList = new Gson().toJson(metadataList);
			// TODO paralleliser
			MultivaluedMap<String, String> iconsQueryParams = new MultivaluedMapImpl();
			iconsQueryParams.add("mdids", jsonMetadataList);
			ClientResponse thumbsWebServiceResponse = thumbsWebServiceResource
					.queryParams(iconsQueryParams)
					.accept(MediaType.APPLICATION_XML_TYPE)
					.get(ClientResponse.class);
			Document iconsResponse = thumbsWebServiceResponse
					.getEntity(Document.class);
			if (iconsResponse != null)
				WebServicesResponseMerger.addThumbsReferences(metadataResponse,
						iconsResponse);
		}

		if (requestedFormat.equals(CustomMediaType.JSONP.toString())) {
			String jsonSource = JSonUtils.convertXMLToJson(metadataResponse);
			Object metadataResponseJson = new JSONWithPadding(jsonSource,
					callBack);
			return Response
					.ok(metadataResponseJson, "application/x-javascript")
					.build();
		}
		return Response.ok(metadataResponse, MediaType.APPLICATION_XML)
				.header("Access-Control-Allow-Origin", "*").build();
	}

	private Response searchMetadata(String query, String callBack, float fuzzy,
			String staticFilters, String dynamicFilters, int start, int rows,
			String requestedFormat, boolean addThumbs)
			throws MetadataRepositoryFailureException, ClientHandlerException,
			UniformInterfaceException {
		MultivaluedMap<String, String> queryParams = new MultivaluedMapImpl();
		queryParams.add("query", query);
		queryParams.add("fuzzy", Float.toString(fuzzy));
		HashMap<String, NodeList> metadataFromExtractedResponse = null;
		// ask content web service for resources matching this query
		Document contentResponse = null;
		if (!StringUtils.isBlank(query)) {
			try {
				ClientResponse contentWebServiceResponse = contentWebServiceResource
						.path("resource").queryParams(queryParams)
						.accept(MediaType.APPLICATION_XML_TYPE)
						.get(ClientResponse.class);
				contentResponse = contentWebServiceResponse
						.getEntity(Document.class);
				// extract metadata from content web service response
				metadataFromExtractedResponse = WebServicesResponseMerger
						.collectMetadataAndHits(contentResponse);
				queryParams.add("supplements", StringUtils.join(
						metadataFromExtractedResponse.keySet(), ","));
			} catch (ClientHandlerException e) {
				logger.error("Connexion to content search aborted for timeout : "
						+ e.getMessage());
				e.printStackTrace();
			}
		}
		// complete query with metadata specific fields
		queryParams.add("static-filters", staticFilters);
		queryParams.add("dynamic-filters", dynamicFilters);
		queryParams.add("start", Integer.toString(start));
		queryParams.add("rows", Integer.toString(rows));
		queryParams.add("desc", "true");
		// ask metadata web service
		ClientResponse metadataWebServiceResponse = null;
		try {
			metadataWebServiceResponse = metadataWebServiceResource
					.queryParams(queryParams)
					.accept(MediaType.APPLICATION_XML_TYPE)
					.get(ClientResponse.class);
		} catch (ClientHandlerException e) {
			e.printStackTrace();

			throw new MetadataRepositoryFailureException(
					"Timeout for metadata repository request " + e.getMessage());
		}

		if (metadataWebServiceResponse.getStatus() != Status.OK.getStatusCode()) {
			throw new MetadataRepositoryFailureException(
					metadataWebServiceResponse.getEntity(String.class));
		}
		Document metadataResponse = metadataWebServiceResponse
				.getEntity(Document.class);
		if (metadataFromExtractedResponse != null)
			WebServicesResponseMerger.mergeHits(metadataResponse,
					metadataFromExtractedResponse);
		if (contentResponse != null)
			WebServicesResponseMerger.mergeSpellChecks(contentResponse,
					metadataResponse);

		// Ask thumbs web services for thumbs.
		// not necessary if thumbs uris have been reported into scolomfr files
		if (addThumbs) {
			List<String> metadataList = WebServicesResponseMerger
					.extractMetadataList(metadataResponse);
			String jsonMetadataList = new Gson().toJson(metadataList);
			MultivaluedMap<String, String> iconsQueryParams = new MultivaluedMapImpl();
			iconsQueryParams.add("mdids", jsonMetadataList);
			ClientResponse thumbsWebServiceResponse = thumbsWebServiceResource
					.queryParams(iconsQueryParams)
					.accept(MediaType.APPLICATION_XML_TYPE)
					.get(ClientResponse.class);
			Document iconsResponse = thumbsWebServiceResponse
					.getEntity(Document.class);
			if (iconsResponse != null)
				WebServicesResponseMerger.addThumbsReferences(metadataResponse,
						iconsResponse);
		}

		if (requestedFormat.equals(CustomMediaType.JSONP.toString())) {
			String jsonSource = JSonUtils.convertXMLToJson(metadataResponse);
			Object metadataResponseJson = new JSONWithPadding(jsonSource,
					callBack);
			return Response
					.ok(metadataResponseJson, "application/x-javascript")
					.build();
		}
		return Response.ok(metadataResponse, MediaType.APPLICATION_XML)
				.header("Access-Control-Allow-Origin", "*").build();
	}

	@GET
	@Path("/suggestions")
	@Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_ATOM_XML,
			MediaType.TEXT_HTML, MediaType.APPLICATION_XHTML_XML,
			"application/javascript" })
	public Response getSuggestions(
			@Context HttpServletRequest request,
			@QueryParam(value = "query") final String query,
			@DefaultValue("handleSuggestionResult") @QueryParam(value = "callback") final String callBack,
			@QueryParam(value = "format") final String format) {

		String requestedFormat = guessRequestedFormat(request, format);
		if (StringUtils.isBlank(query))
			return Response
					.status(Status.BAD_REQUEST)
					.entity("You  cannot ask for suggestions with a blank query string")
					.type(MediaType.TEXT_PLAIN).build();

		UUID contentRequestIdentifier = UUID.randomUUID();
		UUID metadataRequestIdentifier = UUID.randomUUID();
		SuggestQueryWorker contentSuggestQueryWorker = new SuggestQueryWorker(
				query, contentWebServiceResource, this,
				contentRequestIdentifier);
		SuggestQueryWorker metaSuggestQueryWorker = new SuggestQueryWorker(
				query, metadataWebServiceResource, this,
				metadataRequestIdentifier);
		Thread contentRequestThread = new Thread(contentSuggestQueryWorker);
		Thread metaRequestThread = new Thread(metaSuggestQueryWorker);
		contentRequestThread.start();
		metaRequestThread.start();
		try {
			// TODO mettre un timeout ?
			contentRequestThread.join();
			metaRequestThread.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Document contentResponse = requestWorkersResponses
				.get(contentRequestIdentifier);
		Document metadataResponse = requestWorkersResponses
				.get(metadataRequestIdentifier);
		WebServicesResponseMerger.mergeSpellChecks(contentResponse,
				metadataResponse);
		if (requestedFormat.equals(MediaType.APPLICATION_JSON)) {
			String jsonSource = JSonUtils.convertXMLToJson(metadataResponse);
			Object metadataResponseJson = new JSONWithPadding(jsonSource,
					callBack);
			return Response
					.ok(metadataResponseJson, "application/x-javascript")
					.build();
		}

		return Response.ok(metadataResponse, MediaType.APPLICATION_XML)
				.header("Access-Control-Allow-Origin", "*").build();
	}

	public void notifyRequestTermination(UUID identifier, Document response) {
		requestWorkersResponses.put(identifier, response);
	}

}
