package org.fluxtream.connectors.moves;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;
import org.fluxtream.SimpleTimeInterval;
import org.fluxtream.TimeInterval;
import org.fluxtream.TimeUnit;
import org.fluxtream.connectors.Connector;
import org.fluxtream.connectors.ObjectType;
import org.fluxtream.connectors.bodytrackResponders.AbstractBodytrackResponder;
import org.fluxtream.connectors.vos.AbstractFacetVO;
import org.fluxtream.domain.AbstractFacet;
import org.fluxtream.domain.ApiKey;
import org.fluxtream.domain.GuestSettings;
import org.fluxtream.mvc.models.TimespanModel;
import org.fluxtream.services.impl.BodyTrackHelper;
import org.springframework.stereotype.Component;

@Component
public class MovesBodytrackResponder extends AbstractBodytrackResponder {

    @Override
    public List<TimespanModel> getTimespans(final long startMillis, final long endMillis, final ApiKey apiKey, final String channelName) {
        List<TimespanModel> items = new ArrayList<TimespanModel>();
        final TimeInterval timeInterval = new SimpleTimeInterval(startMillis, endMillis, TimeUnit.ARBITRARY, TimeZone.getTimeZone("UTC"));
        ObjectType[] objectTypes = apiKey.getConnector().objectTypes();

        for (ObjectType objectType : objectTypes){
            String objectTypeName = apiKey.getConnector().getName() + "-" + objectType.getName();
            if (objectType.getName().equals("move")){
                List<AbstractFacet> facets = getFacetsInTimespan(timeInterval,apiKey,objectType);
                for (AbstractFacet facet : facets){
                    MovesMoveFacet moveFacet = (MovesMoveFacet) facet;
                    for (MovesActivity activity : moveFacet.getActivities()){
                        BodyTrackHelper.TimespanStyle style = new BodyTrackHelper.TimespanStyle();
                        style.iconURL = "/images/moves/" + activity.activity + ".png";
                        final TimespanModel moveTimespanModel = new TimespanModel(activity.start, activity.end, activity.activity, objectTypeName, style);
                        items.add(moveTimespanModel);
                    }
                }

            }
            else if (objectType.getName().equals("place")){
                List<AbstractFacet> facets = getFacetsInTimespan(timeInterval,apiKey,objectType);
                for (AbstractFacet facet : facets){
                    MovesPlaceFacet place = (MovesPlaceFacet) facet;
                    BodyTrackHelper.TimespanStyle style = new BodyTrackHelper.TimespanStyle();
                    style.iconURL = "/moves/place/" + place.apiKeyId + "/" + place.getId();
                    final TimespanModel placeTimespanModel = new TimespanModel(place.start, place.end, "place", objectTypeName,style);
                    items.add(placeTimespanModel);
                }
            }
        }
        return items;
    }

    @Override
    public List<AbstractFacetVO<AbstractFacet>> getFacetVOs(final GuestSettings guestSettings, final ApiKey apiKey, final String objectTypeName, final long start, final long end, final String value) {
        Connector connector = apiKey.getConnector();
        String[] objectTypeNameParts = objectTypeName.split("-");
        ObjectType objectType = null;
        for (ObjectType ot : connector.objectTypes()){
            if (ot.getName().equals(objectTypeNameParts[1])){
                objectType = ot;
                break;
            }
        }
        if (objectType == null || (objectType.getName().equals("place") && !"place".equals(value)))
            return new ArrayList<AbstractFacetVO<AbstractFacet>>();

        TimeInterval timeInterval = metadataService.getArbitraryTimespanMetadata(apiKey.getGuestId(), start, end).getTimeInterval();

        List<AbstractFacet> facets = getFacetsInTimespan(timeInterval,apiKey,objectType);

        if (objectType.getName().equals("move")){
            MovesMoveFacet move;
            for (Iterator<AbstractFacet> i = facets.iterator(); i.hasNext();){
                move = (MovesMoveFacet) i.next();
                boolean found = false;
                for (MovesActivity activity : move.getActivities()){
                    if (activity.activity.equals(value)){
                        found = true;
                        break;
                    }
                }
                if (!found)
                   i.remove();
            }
        }

        List<AbstractFacetVO<AbstractFacet>> facetVOsForFacets = getFacetVOsForFacets(facets, timeInterval, guestSettings);
        facetVOsForFacets = dedup(facetVOsForFacets);
        return facetVOsForFacets;
    }

    private List<AbstractFacetVO<AbstractFacet>> dedup(final List<AbstractFacetVO<AbstractFacet>> facetVOs) {
        List<AbstractFacetVO<AbstractFacet>> deduped = new ArrayList<AbstractFacetVO<AbstractFacet>>();

        there: for (AbstractFacetVO<AbstractFacet> facetVO : facetVOs) {
            AbstractMovesFacetVO f = (AbstractMovesFacetVO) facetVO;
            for (AbstractFacetVO<AbstractFacet> uniqueFacet : deduped) {
                AbstractMovesFacetVO u = (AbstractMovesFacetVO) uniqueFacet;
                if (u.type.equals(f.type)&&u.start==f.start) {
                    if (u.end>f.end)
                        continue there;
                    else {
                        deduped.remove(u);
                        deduped.add(f);
                        continue there;
                    }
                }
            }
            deduped.add(f);
        }
        return deduped;
    }

}