package com.tarento.analytics.helper;

import com.tarento.analytics.dto.AggregateRequestDto;
import com.tarento.analytics.dto.Data;
import com.tarento.analytics.dto.Plot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ComputedFieldHelper {

    public static final Logger logger = LoggerFactory.getLogger(ComputedFieldHelper.class);

    @Autowired
    private ComputeHelperFactory computeHelperFactory;

    private AggregateRequestDto aggregateRequestDto;
    private String postAggrTheoryName;

    private String string="percentage";



    public void set(AggregateRequestDto requestDto, String postAggrTheoryName){

        this.aggregateRequestDto = requestDto;
        this.postAggrTheoryName = postAggrTheoryName;
    }

    public void add(Data data, String newfield, String partField, String wholeField){
        try {
            Map<String, Plot> plotMap = data.getPlots().stream().parallel().collect(Collectors.toMap(Plot::getName, Function.identity()));

            if (plotMap.get(partField).getValue() == 0.0 || plotMap.get(wholeField).getValue() == 0.0) {

                data.getPlots().add(new Plot(newfield, 0.0, string));
            } else {
                double wholeValue = plotMap.get(wholeField).getValue();
                double fieldValue = plotMap.get(partField).getValue() / plotMap.get(wholeField).getValue() * 100;

                if(postAggrTheoryName != null && !postAggrTheoryName.isEmpty()) {
                    //logger.info("Chart name: "+aggregateRequestDto.getVisualizationCode()+" :: postAggrTheoryName : "+postAggrTheoryName);
                    ComputeHelper computeHelper = computeHelperFactory.getInstance(postAggrTheoryName);
                    fieldValue = computeHelper.compute(aggregateRequestDto, fieldValue);
                    wholeValue = computeHelper.compute(aggregateRequestDto, wholeValue);

                }
                data.getPlots().stream().filter(plot -> wholeField.equalsIgnoreCase(plot.getName())).findAny().orElse(null).setValue(wholeValue);
                data.getPlots().add(new Plot(newfield, fieldValue, string));

            }


        } catch (Exception e) {
            data.getPlots().add(new Plot(newfield, 0.0, string));
        }
    }

}
