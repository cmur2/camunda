package org.camunda.optimize.service.engine.importing.fetcher.instance;

import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.index.page.IdSetBasedImportPage;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.INCLUDE_PROCESS_INSTANCE_IDS;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class RunningProcessInstanceFetcher extends
  RetryBackoffEngineEntityFetcher<HistoricProcessInstanceDto> {

  public RunningProcessInstanceFetcher(EngineContext engineContext) {
    super(engineContext);
  }

  public List<HistoricProcessInstanceDto> fetchHistoricProcessInstances(IdSetBasedImportPage page) {
    return fetchHistoricProcessInstances(page.getIds());
  }

  private List<HistoricProcessInstanceDto> fetchHistoricProcessInstances(Set<String> processInstanceIds) {

    long requestStart = System.currentTimeMillis();

    Map<String, Set<String>> pids = new HashMap<>();
    pids.put(INCLUDE_PROCESS_INSTANCE_IDS, processInstanceIds);
    List<HistoricProcessInstanceDto> entries =
      fetchWithRetry(() -> performRunningHistoricProcessInstanceRequest(pids));

    long requestEnd = System.currentTimeMillis();
    logger.debug(
      "Fetched [{}] unfinished process instances within [{}] ms",
      entries.size(),
      requestEnd - requestStart
    );
    return entries;
  }

  private List<HistoricProcessInstanceDto> performRunningHistoricProcessInstanceRequest(Map<String, Set<String>> pids) {
    return getEngineClient()
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
      .path(configurationService.getHistoricProcessInstanceEndpoint())
      .request(MediaType.APPLICATION_JSON)
      .acceptEncoding(UTF8)
      .post(Entity.entity(pids, MediaType.APPLICATION_JSON))
      .readEntity(new GenericType<List<HistoricProcessInstanceDto>>() {
      });
  }
}
