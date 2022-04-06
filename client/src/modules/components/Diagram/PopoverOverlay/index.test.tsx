/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {rest} from 'msw';
import {render, screen} from '@testing-library/react';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {mockServer} from 'modules/mock-server/node';
import {PopoverOverlay} from './';
import {
  createInstance,
  mockCallActivityProcessXML,
  mockProcessXML,
} from 'modules/testUtils';
import {mockIncidents} from 'modules/mocks/incidents';
import {MOCK_TIMESTAMP} from 'modules/utils/date/__mocks__/formatDate';
import userEvent from '@testing-library/user-event';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {incidentsStore} from 'modules/stores/incidents';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {
  calledDecisionMetadata,
  calledFailedDecisionMetadata,
  calledInstanceMetadata,
  calledUnevaluatedDecisionMetadata,
  incidentFlowNodeMetaData,
  multiInstanceCallActivityMetadata,
  multiInstancesMetadata,
  rootIncidentFlowNodeMetaData,
  CALL_ACTIVITY_FLOW_NODE_ID,
  PROCESS_INSTANCE_ID,
  FLOW_NODE_ID,
} from 'modules/mocks/metadata';
import {metadataDemoProcess} from 'modules/mocks/metadataDemoProcess';
import {LocationLog} from 'modules/utils/LocationLog';

const Wrapper: React.FC = ({children}) => {
  return (
    <ThemeProvider>
      <MemoryRouter initialEntries={['/processes/1']}>
        <Routes>
          <Route path="/processes/:processInstanceId" element={children} />
          <Route path="/decisions/:decisionInstanceId" element={<></>} />
        </Routes>
        <LocationLog />
      </MemoryRouter>
    </ThemeProvider>
  );
};

const renderPopover = () => {
  const {container} = render(<svg />);

  render(
    <PopoverOverlay selectedFlowNodeRef={container.querySelector('svg')} />,
    {
      wrapper: Wrapper,
    }
  );
};

describe('PopoverOverlay', () => {
  beforeEach(() => {
    flowNodeMetaDataStore.init();
    flowNodeSelectionStore.init();
    processInstanceDetailsDiagramStore.init();
  });

  afterEach(() => {
    flowNodeMetaDataStore.reset();
    flowNodeSelectionStore.reset();
    processInstanceDetailsStore.reset();
    incidentsStore.reset();
    processInstanceDetailsDiagramStore.reset();
  });

  it('should render meta data for incident flow node', async () => {
    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXML))
      ),
      rest.post(
        `/api/process-instances/${PROCESS_INSTANCE_ID}/flow-node-metadata`,
        (_, res, ctx) => res.once(ctx.json(incidentFlowNodeMetaData))
      ),
      rest.get(
        `/api/process-instances/${PROCESS_INSTANCE_ID}/incidents`,
        (_, res, ctx) => res.once(ctx.json(mockIncidents))
      )
    );
    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'INCIDENT',
      })
    );
    incidentsStore.init();

    flowNodeSelectionStore.selectFlowNode({flowNodeId: FLOW_NODE_ID});

    renderPopover();

    expect(
      await screen.findByText(/Flow Node Instance Id/)
    ).toBeInTheDocument();
    expect(screen.getByText(/Start Date/)).toBeInTheDocument();
    expect(screen.getByText(/End Date/)).toBeInTheDocument();
    expect(screen.getByText(/Type/)).toBeInTheDocument();
    expect(screen.getByText(/Error Message/)).toBeInTheDocument();
    expect(screen.getAllByText(/View/)).toHaveLength(2);
    expect(screen.queryByText(/Called Instance/)).not.toBeInTheDocument();

    const {incident, instanceMetadata} = incidentFlowNodeMetaData;

    expect(
      screen.getByText(instanceMetadata!.flowNodeInstanceId)
    ).toBeInTheDocument();
    expect(screen.getByText(MOCK_TIMESTAMP)).toBeInTheDocument();
    expect(screen.getByText(incident.errorMessage)).toBeInTheDocument();
    expect(screen.getByText(incident.errorType.name)).toBeInTheDocument();
    expect(
      screen.getByText(
        `${incident.rootCauseInstance.processDefinitionName} - ${incident.rootCauseInstance.instanceId}`
      )
    );
  });

  it('should render meta data for completed flow node', async () => {
    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockCallActivityProcessXML))
      ),
      rest.post(
        `/api/process-instances/${PROCESS_INSTANCE_ID}/flow-node-metadata`,
        (_, res, ctx) => res.once(ctx.json(calledInstanceMetadata))
      )
    );
    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      })
    );
    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: CALL_ACTIVITY_FLOW_NODE_ID,
    });

    renderPopover();

    expect(
      await screen.findByText(/Flow Node Instance Id/)
    ).toBeInTheDocument();
    expect(screen.getByText(/Start Date/)).toBeInTheDocument();
    expect(screen.getByText(/End Date/)).toBeInTheDocument();
    expect(screen.getByText(/Called Instance/)).toBeInTheDocument();
    expect(screen.getByText(/View/)).toBeInTheDocument();

    expect(
      screen.getByText(
        calledInstanceMetadata.instanceMetadata!.flowNodeInstanceId
      )
    ).toBeInTheDocument();
    expect(screen.getAllByText(MOCK_TIMESTAMP)).toHaveLength(2);
    expect(
      screen.getByText(
        `Called Process - ${
          calledInstanceMetadata.instanceMetadata!.calledProcessInstanceId
        }`
      )
    ).toBeInTheDocument();

    expect(screen.queryByText(/incidentErrorType/)).not.toBeInTheDocument();
    expect(screen.queryByText(/incidentErrorMessage/)).not.toBeInTheDocument();
  });

  it('should render meta data modal', async () => {
    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockCallActivityProcessXML))
      ),
      rest.post(
        `/api/process-instances/${PROCESS_INSTANCE_ID}/flow-node-metadata`,
        (_, res, ctx) => res.once(ctx.json(calledInstanceMetadata))
      )
    );
    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      })
    );
    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: CALL_ACTIVITY_FLOW_NODE_ID,
    });

    renderPopover();

    expect(
      await screen.findByText(/Flow Node Instance Id/)
    ).toBeInTheDocument();

    const [firstViewLink] = screen.getAllByText(/View/);
    expect(firstViewLink).toBeInTheDocument();

    userEvent.click(firstViewLink!);

    expect(
      screen.getByText(/Flow Node "Activity_0zqism7" Metadata/)
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'Close Modal'})
    ).toBeInTheDocument();

    expect(
      screen.getByText(/"flowNodeId": "Activity_0zqism7"/)
    ).toBeInTheDocument();
    expect(
      screen.getByText(/"flowNodeInstanceId": "2251799813699889"/)
    ).toBeInTheDocument();
    expect(
      screen.getByText(/"flowNodeType": "TASK_CALL_ACTIVITY"/)
    ).toBeInTheDocument();
    expect(
      screen.getByText(/"startDate": "2018-12-12 00:00:00"/)
    ).toBeInTheDocument();
    expect(
      screen.getByText(/"endDate": "2018-12-12 00:00:00"/)
    ).toBeInTheDocument();
    expect(
      screen.getByText(/"jobDeadline": "2018-12-12 00:00:00"/)
    ).toBeInTheDocument();
    expect(screen.getByText(/"incidentErrorType": null/)).toBeInTheDocument();
    expect(
      screen.getByText(/"incidentErrorMessage": null/)
    ).toBeInTheDocument();
    expect(screen.getByText(/"jobId": null/)).toBeInTheDocument();
    expect(screen.getByText(/"jobType": null/)).toBeInTheDocument();
    expect(screen.getByText(/"jobRetries": null/)).toBeInTheDocument();
    expect(screen.getByText(/"jobWorker": null/)).toBeInTheDocument();
    expect(screen.getByText(/"jobCustomHeaders": null/)).toBeInTheDocument();
    expect(
      screen.getByText(/"calledProcessInstanceId": "229843728748927482"/)
    ).toBeInTheDocument();
  });

  it('should render metadata for multi instance flow nodes', async () => {
    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXML))
      ),
      rest.post(
        `/api/process-instances/:processInstanceId/flow-node-metadata`,
        (_, res, ctx) => res.once(ctx.json(multiInstancesMetadata))
      )
    );
    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: '123',
        state: 'ACTIVE',
      })
    );
    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: FLOW_NODE_ID,
    });

    renderPopover();

    expect(
      await screen.findByText(/There are 10 Instances/)
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        /To view details for any of these,\s*select one Instance in the Instance History./
      )
    ).toBeInTheDocument();
    expect(screen.getByText(/3 incidents occured/)).toBeInTheDocument();
    expect(screen.getByText(/View/)).toBeInTheDocument();
    expect(screen.queryByText(/Flow Node Instance Id/)).not.toBeInTheDocument();
  });

  it('should not render called instances for multi instance call activities', async () => {
    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXML))
      ),
      rest.post(
        `/api/process-instances/:processInstanceId/flow-node-metadata`,
        (_, res, ctx) => res.once(ctx.json(multiInstanceCallActivityMetadata))
      )
    );
    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      })
    );
    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: CALL_ACTIVITY_FLOW_NODE_ID,
    });

    renderPopover();

    expect(
      await screen.findByText(/Flow Node Instance Id/)
    ).toBeInTheDocument();
    expect(screen.queryByText(/Called Instance/)).not.toBeInTheDocument();
  });

  it('should not render root cause instance link when instance is root', async () => {
    const {rootCauseInstance} = rootIncidentFlowNodeMetaData.incident;

    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXML))
      ),
      rest.post(
        `/api/process-instances/${PROCESS_INSTANCE_ID}/flow-node-metadata`,
        (_, res, ctx) => res.once(ctx.json(rootIncidentFlowNodeMetaData))
      ),
      rest.get(
        `/api/process-instances/${PROCESS_INSTANCE_ID}/incidents`,
        (_, res, ctx) => res.once(ctx.json(mockIncidents))
      )
    );
    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'INCIDENT',
      })
    );
    incidentsStore.init();

    flowNodeSelectionStore.selectFlowNode({flowNodeId: FLOW_NODE_ID});

    renderPopover();

    expect(await screen.findByText(/Root Cause Instance/)).toBeInTheDocument();
    expect(screen.getByText(/Current Instance/)).toBeInTheDocument();
    expect(
      screen.queryByText(
        `${rootCauseInstance.processDefinitionName} - ${rootCauseInstance.instanceId}`
      )
    ).not.toBeInTheDocument();
  });

  it('should render completed decision', async () => {
    const {instanceMetadata} = calledDecisionMetadata;

    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(metadataDemoProcess))
      ),
      rest.post(
        `/api/process-instances/${PROCESS_INSTANCE_ID}/flow-node-metadata`,
        (_, res, ctx) => res.once(ctx.json(calledDecisionMetadata))
      )
    );
    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'COMPLETED',
      })
    );

    flowNodeSelectionStore.selectFlowNode({flowNodeId: 'BusinessRuleTask'});

    renderPopover();

    expect(await screen.findByText(/called decision/i)).toBeInTheDocument();
    expect(screen.queryByText(/incident/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/root cause decision/i)).not.toBeInTheDocument();

    userEvent.click(
      screen.getByText(
        `${instanceMetadata!.calledDecisionDefinitionName} - ${
          instanceMetadata!.calledDecisionInstanceId
        }`
      )
    );

    expect(screen.getByTestId('pathname')).toHaveTextContent(
      `/decisions/${instanceMetadata!.calledDecisionInstanceId}`
    );
  });

  it('should render failed decision', async () => {
    const {instanceMetadata} = calledFailedDecisionMetadata;
    const {rootCauseDecision} = calledFailedDecisionMetadata!.incident!;

    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(metadataDemoProcess))
      ),
      rest.post(
        `/api/process-instances/${PROCESS_INSTANCE_ID}/flow-node-metadata`,
        (_, res, ctx) => res.once(ctx.json(calledFailedDecisionMetadata))
      )
    );
    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'INCIDENT',
      })
    );

    flowNodeSelectionStore.selectFlowNode({flowNodeId: 'BusinessRuleTask'});

    renderPopover();

    expect(await screen.findByText(/called decision/i)).toBeInTheDocument();
    expect(screen.getByText(/incident/i)).toBeInTheDocument();
    expect(
      screen.getByText(
        `${instanceMetadata!.calledDecisionDefinitionName} - ${
          instanceMetadata!.calledDecisionInstanceId
        }`
      )
    ).toBeInTheDocument();
    expect(screen.getByText(/root cause decision/i)).toBeInTheDocument();
    expect(screen.queryByText(/root cause instance/i)).not.toBeInTheDocument();

    userEvent.click(
      screen.getByText(
        `${rootCauseDecision!.decisionName!} - ${rootCauseDecision!.instanceId}`
      )
    );

    expect(screen.getByTestId('pathname')).toHaveTextContent(
      `/decisions/${rootCauseDecision!.instanceId}`
    );
  });

  it('should render unevaluated decision', async () => {
    const {instanceMetadata} = calledUnevaluatedDecisionMetadata;

    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(metadataDemoProcess))
      ),
      rest.post(
        `/api/process-instances/${PROCESS_INSTANCE_ID}/flow-node-metadata`,
        (_, res, ctx) => res.once(ctx.json(calledUnevaluatedDecisionMetadata))
      )
    );
    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      })
    );

    flowNodeSelectionStore.selectFlowNode({flowNodeId: 'BusinessRuleTask'});

    renderPopover();

    expect(await screen.findByText(/called decision/i)).toBeInTheDocument();
    expect(
      screen.getByText(instanceMetadata.calledDecisionDefinitionName)
    ).toBeInTheDocument();
    expect(screen.queryByText(/incident/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/root cause decision/i)).not.toBeInTheDocument();
  });
});
