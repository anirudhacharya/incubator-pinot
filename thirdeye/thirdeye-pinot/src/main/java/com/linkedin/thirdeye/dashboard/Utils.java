package com.linkedin.thirdeye.dashboard;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.linkedin.thirdeye.api.CollectionSchema;
import com.linkedin.thirdeye.api.TimeGranularity;
import com.linkedin.thirdeye.client.MetricExpression;
import com.linkedin.thirdeye.client.MetricFunction;
import com.linkedin.thirdeye.client.QueryCache;
import com.linkedin.thirdeye.client.ThirdEyeClient;
import com.linkedin.thirdeye.client.ThirdEyeRequest;
import com.linkedin.thirdeye.client.ThirdEyeRequest.ThirdEyeRequestBuilder;
import com.linkedin.thirdeye.client.pinot.PinotThirdEyeClient;
import com.linkedin.thirdeye.client.pinot.PinotThirdEyeClientConfig;
import com.linkedin.thirdeye.common.ThirdEyeConfiguration;
import com.linkedin.thirdeye.client.ThirdEyeResponse;
import com.linkedin.thirdeye.dashboard.configs.AbstractConfigDAO;
import com.linkedin.thirdeye.dashboard.configs.DashboardConfig;

public class Utils {
  private static final Logger LOG = LoggerFactory.getLogger(Utils.class);

  private static String DEFAULT_DASHBOARD = "dafaultDashboard";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public static List<ThirdEyeRequest> generateRequests(String collection, String requestReference,
      MetricFunction metricFunction, List<String> dimensions, DateTime start, DateTime end) {

    List<ThirdEyeRequest> requests = new ArrayList<>();

    for (String dimension : dimensions) {
      ThirdEyeRequestBuilder requestBuilder = new ThirdEyeRequestBuilder();
      requestBuilder.setCollection(collection);
      List<MetricFunction> metricFunctions = Arrays.asList(metricFunction);
      requestBuilder.setMetricFunctions(metricFunctions);

      requestBuilder.setStartTimeInclusive(start);
      requestBuilder.setEndTimeExclusive(end);
      requestBuilder.setGroupBy(dimension);

      ThirdEyeRequest request = requestBuilder.build(requestReference);
      requests.add(request);
    }

    return requests;

  }

  public static Map<String, List<String>> getFilters(QueryCache queryCache, String collection,
      String requestReference, String metricName, List<String> dimensions, DateTime start,
      DateTime end) throws Exception {

    MetricFunction metricFunction = new MetricFunction(MetricFunction.SUM, "__COUNT");

    List<ThirdEyeRequest> requests =
        generateRequests(collection, requestReference, metricFunction, dimensions, start, end);

    Map<ThirdEyeRequest, Future<ThirdEyeResponse>> queryResultMap =
        queryCache.getQueryResultsAsync(requests);

    Map<String, List<String>> result = new HashMap<>();
    for (Map.Entry<ThirdEyeRequest, Future<ThirdEyeResponse>> entry : queryResultMap.entrySet()) {
      ThirdEyeRequest request = entry.getKey();
      String dimension = request.getGroupBy().get(0);
      ThirdEyeResponse thirdEyeResponse = entry.getValue().get();
      int n = thirdEyeResponse.getNumRowsFor(metricFunction);

      List<String> values = new ArrayList<>();
      for (int i = 0; i < n; i++) {
        Map<String, String> row = thirdEyeResponse.getRow(metricFunction, i);
        String dimensionValue = row.get(dimension);
        values.add(dimensionValue);
      }

      result.put(dimension, values);

    }

    return result;
  }

  public static List<String> getDimensions(QueryCache queryCache, String collection)
      throws Exception {
    CollectionSchema schema = queryCache.getClient().getCollectionSchema(collection);
    List<String> dimensions = schema.getDimensionNames();
    Collections.sort(dimensions);

    return dimensions;
  }

  public static List<String> getDimensionsToGroupBy(QueryCache queryCache, String collection,
      Multimap<String, String> filters) throws Exception {
    List<String> dimensions = Utils.getDimensions(queryCache, collection);

    List<String> dimensionsToGroupBy = new ArrayList<>();
    if (filters != null) {
      Set<String> filterDimenions = filters.keySet();
      for (String dimension : dimensions) {
        if (!filterDimenions.contains(dimension)) {
          // dimensions.remove(dimension);
          dimensionsToGroupBy.add(dimension);
        }
      }
    } else {
      return dimensions;
    }

    return dimensionsToGroupBy;
  }

  public static List<String> getDashboards(AbstractConfigDAO<DashboardConfig> configDAO,
      String collection) {
    List<DashboardConfig> dashboardConfigs = configDAO.findAll(collection);

    List<String> dashboards = new ArrayList<>();
    for (DashboardConfig dashboardConfig : dashboardConfigs) {
      if (dashboardConfig == null) {
        continue;
      } else {
        dashboards.add(dashboardConfig.getDashboardName());
      }
    }
    if (dashboards == null || dashboards.isEmpty()) {
      dashboards.add("Default_All_Metrics_Dashboard");
    }
    return dashboards;
  }

  public static List<MetricExpression> getMetricExpressions(
      AbstractConfigDAO<DashboardConfig> configDAO, String collection, String dashboardId) {

    DashboardConfig dashboardConfig = configDAO.findById(collection, dashboardId);
    return dashboardConfig.getMetricExpressions();
  }

  public static List<MetricFunction> getMetricFunctions(
      AbstractConfigDAO<DashboardConfig> configDAO, String collection, String dashboardId,
      String metricsJson) throws JsonParseException, JsonMappingException, IOException {

    List<MetricFunction> metricFunctions = new ArrayList<>();
    if (StringUtils.isBlank(dashboardId) && StringUtils.isBlank(metricsJson)) {
      DashboardConfig dashboardConfig = configDAO.findById(collection, DEFAULT_DASHBOARD);
      metricFunctions.add(new MetricFunction(MetricFunction.SUM, "__COUNT"));
      // metricFunctions = dashboardConfig.getMetricFunctions();
    } else if (StringUtils.isNotBlank(metricsJson)) {
      ArrayList<String> metrics =
          OBJECT_MAPPER.readValue(metricsJson, new TypeReference<ArrayList<String>>() {
          });
      for (String metric : metrics) {
        metricFunctions.add(new MetricFunction(MetricFunction.SUM, metric));
      }

    } else {
      DashboardConfig dashboardConfig = configDAO.findById(collection, dashboardId);
      metricFunctions.add(new MetricFunction(MetricFunction.SUM, "__COUNT"));
      // metricFunctions = dashboardConfig.getMetricFunctions();
    }

    return metricFunctions;

  }

  public static void main(String[] args) {

  }

  public static Multimap<String, String> convertToMultiMap(String filterJson) {
    ArrayListMultimap<String, String> multimap = ArrayListMultimap.create();
    if (filterJson == null) {
      return multimap;
    }
    try {
      TypeReference<Map<String, ArrayList<String>>> valueTypeRef =
          new TypeReference<Map<String, ArrayList<String>>>() {
          };
      Map<String, ArrayList<String>> map;

      map = OBJECT_MAPPER.readValue(filterJson, valueTypeRef);
      for (Map.Entry<String, ArrayList<String>> entry : map.entrySet()) {
        ArrayList<String> valueList = entry.getValue();
        ArrayList<String> trimmedList = new ArrayList<>();
        for(String value:valueList){
          trimmedList.add(value.trim());
        }
        multimap.putAll(entry.getKey(), trimmedList);
      }
      return multimap;
    } catch (IOException e) {
      LOG.error("Error parsing filter json:{} message:{}", filterJson, e.getMessage());
    }
    return multimap;
  }

  public static List<MetricExpression> convertToMetricExpressions(String metricsJson) {
    List<MetricExpression> metricExpressions = new ArrayList<>();
    if (metricsJson == null) {
      return metricExpressions;
    }
    ArrayList<String> metricExpressionStrings;
    try {
      TypeReference<ArrayList<String>> valueTypeRef = new TypeReference<ArrayList<String>>() {
      };

      metricExpressionStrings = OBJECT_MAPPER.readValue(metricsJson, valueTypeRef);
    } catch (Exception e) {
      LOG.error("Error parsing metrics json: {} errorMessage:{}", metricsJson, e.getMessage());
      metricExpressionStrings = new ArrayList<>();
      String[] metrics = metricsJson.split(",");
      for (String metric : metrics) {
        metricExpressionStrings.add(metric);
      }
    }
    for (String metricExpressionString : metricExpressionStrings) {
      metricExpressions.add(new MetricExpression(metricExpressionString));
    }

    return metricExpressions;
  }

  public static List<MetricFunction> computeMetricFunctionsFromExpressions(
      List<MetricExpression> metricExpressions) {
    List<MetricFunction> metricFunctions = new ArrayList<>();

    for (MetricExpression expression : metricExpressions) {
      metricFunctions.addAll(expression.computeMetricFunctions());
    }
    return metricFunctions;
  }

  public static TimeGranularity getAggregationTimeGranularity(String aggTimeGranularity) {

    TimeGranularity timeGranularity;
    if (aggTimeGranularity.indexOf("_") > -1) {
      String[] split = aggTimeGranularity.split("_");
      timeGranularity = new TimeGranularity(Integer.parseInt(split[0]), TimeUnit.valueOf(split[1]));
    } else {
      timeGranularity = new TimeGranularity(1, TimeUnit.valueOf(aggTimeGranularity));
    }
    return timeGranularity;
  }

  public static List<MetricExpression> convertToMetricExpressions(
      List<MetricFunction> metricFunctions) {
    List<MetricExpression> metricExpressions = new ArrayList<>();
    for (MetricFunction function : metricFunctions) {
      metricExpressions.add(new MetricExpression(function.getMetricName()));
    }
    return metricExpressions;
  }
}
