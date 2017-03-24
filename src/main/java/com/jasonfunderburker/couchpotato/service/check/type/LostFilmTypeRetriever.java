package com.jasonfunderburker.couchpotato.service.check.type;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.gargoylesoftware.htmlunit.StringWebResponse;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.*;
import com.gargoylesoftware.htmlunit.xml.XmlPage;
import com.jasonfunderburker.couchpotato.domain.TorrentItem;
import com.jasonfunderburker.couchpotato.domain.TorrentState;
import com.jasonfunderburker.couchpotato.domain.TorrentType;
import com.jasonfunderburker.couchpotato.domain.rss.RSSFeed;
import com.jasonfunderburker.couchpotato.domain.rss.RSSFeedMessage;
import com.jasonfunderburker.couchpotato.exceptions.TorrentRetrieveException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Optional;

/**
 * Created by JasonFunderburker on 07.09.2016
 */
@Service
public class LostFilmTypeRetriever extends BaseTypeRetriever {
    private static final Logger logger = LoggerFactory.getLogger(LostFilmTypeRetriever.class);
    private static final String LOGIN_PAGE = "https://www.lostfilm.tv/login";
    private static final String RSS_PAGE = "http://www.lostfilm.tv/rss.xml";
    private static final String RSS_DOWLOANDS_PAGE = "http://retre.org/rssdd.xml";
    private final XmlMapper mapper = new XmlMapper();

    public LostFilmTypeRetriever() {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public TorrentState getState(TorrentItem item, final WebClient webClient) throws TorrentRetrieveException, IOException {
        TorrentState result;
        if (item.getState() == null) {
            result = getInitialState(item, webClient);
        } else {
            XmlPage rssPage = webClient.getPage(RSS_PAGE);
            logger.trace("Rss page content={}", rssPage.asXml());
            RSSFeed rss = mapper.readValue(rssPage.asXml(), RSSFeed.class);
            Optional<RSSFeedMessage> rssItem = rss.getChannel().getEntries().stream()
                    .peek(e -> logger.trace("rss item = {}",e))
                    .filter(t -> t.getLink().contains(item.getLink()))
                    .findFirst();
            if (rssItem.isPresent()) {
                logger.debug("rssItem is found = {}", rssItem);
                result = new TorrentState();
                result.setState(rssItem.get().getLink().replace(item.getLink(), "").trim());
                result.setInfo(rssItem.get().getTitle().trim());
            } else {
                logger.debug("Rss feed don't contain info about {}", item.getLink());
                result = item.getState();
            }
            logger.debug("state: {}", result);
        }
        return result;
    }

    private TorrentState getInitialState(TorrentItem item, final WebClient webClient) throws TorrentRetrieveException, IOException {
        logger.debug("getInitialState");
        HtmlPage source = webClient.getPage(item.getLink());
        logger.trace("source {}", source.asText());
        TorrentState result = new TorrentState();
        HtmlTableDataCell state = source.getFirstByXPath("//table[@class='movie-parts-list']//tr[not(@class='not-available')]/td[@class='beta']");
        logger.debug("state {}", state);
        String stateString = state.getAttribute("onClick");
        result.setState(stateString.substring(stateString.indexOf("/season"), stateString.lastIndexOf("'"))+"/");
        logger.debug("state: {}, state as text: {}", state, result.getState());
        return result;
    }

    @Override
    public String getDownloadLink(TorrentItem item, final WebClient webClient) throws TorrentRetrieveException, IOException {
        String downloadLink = null;
        String title = item.getState().getInfo();
        int lastDot = title.lastIndexOf(".");
        String downloadTitle = title.substring(0, lastDot) + title.substring(lastDot+1, title.length());
        logger.debug("downloadTitle={}", downloadTitle);
        XmlPage rssDownloads = webClient.getPage(RSS_DOWLOANDS_PAGE);
        logger.debug("rssDownloads page content={}", rssDownloads.asXml());
        RSSFeed rss = mapper.readValue(rssDownloads.asXml(), RSSFeed.class);
        Optional<RSSFeedMessage> rssItem = rss.getChannel().getEntries().stream()
                .peek(e -> logger.trace("rssDownloads item = {}",e))
                .filter(t -> t.getTitle().contains(downloadTitle))
                .filter(t -> t.getCategory().contains("1080p"))
                .findFirst();
        if (rssItem.isPresent()) {
            logger.debug("rssDownloads is found = {}", rssItem);
            downloadLink = rssItem.get().getLink().trim();
        }
        if (downloadLink == null) throw new TorrentRetrieveException("link for '1080p' is not found");
        webClient.addCookie("", new URL(downloadLink), null);
        webClient.addCookie("", new URL(downloadLink), null);
        return downloadLink;
    }

    @Override
    public void login(TorrentItem item, WebClient webClient) throws TorrentRetrieveException, IOException {
 /*       HtmlPage loginPage = webClient.getPage(LOGIN_PAGE);
        logger.trace("loginPage: {}", loginPage.asText());
        HtmlInput loginInput = loginPage.getElementByName("mail");
        loginInput.type(item.getUserInfo().getUserName());
        String password = item.getUserInfo().getPassword();
        if (password == null)
            throw new TorrentRetrieveException("Login ERROR: please add or refresh your credentials on setting page");
        HtmlInput passInput = loginPage.getElementByName("pass");
        passInput.type(password);
        HtmlInput button = loginPage.getFirstByXPath("//input[@class='primary-btn sign-in-btn' and @type='button']");
        button.click(); */
    }

    @Override
    public TorrentType getTorrentType() {
        return TorrentType.LOST_FILM;
    }
}
