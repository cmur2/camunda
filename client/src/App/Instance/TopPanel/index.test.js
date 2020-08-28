/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {
  render,
  waitForElementToBeRemoved,
  screen,
} from '@testing-library/react';
import {MemoryRouter, Route} from 'react-router-dom';

import {mockSequenceFlows, mockEvents, mockIncidents} from './index.setup';
import PropTypes from 'prop-types';

import SplitPane from 'modules/components/SplitPane';

import {TopPanel} from './index';
import {currentInstance} from 'modules/stores/currentInstance';
import {singleInstanceDiagram} from 'modules/stores/singleInstanceDiagram';
import {
  fetchWorkflowInstance,
  fetchWorkflowInstanceIncidents,
  fetchSequenceFlows,
} from 'modules/api/instances';
import {fetchEvents} from 'modules/api/events';

jest.mock('modules/utils/bpmn');
jest.mock('modules/api/instances');
jest.mock('modules/api/diagram', () => ({
  fetchWorkflowXML: jest.fn().mockImplementation(() => ''),
}));
jest.mock('modules/api/events');

jest.mock('./InstanceHeader', () => {
  return {
    InstanceHeader: () => {
      return <div />;
    },
  };
});

const Wrapper = ({children}) => {
  return (
    <MemoryRouter initialEntries={['/instances/1']}>
      <Route path="/instances/:id">
        <SplitPane>
          {children}
          <SplitPane.Pane />
        </SplitPane>
      </Route>
    </MemoryRouter>
  );
};

Wrapper.propTypes = {
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node,
  ]),
};

describe('TopPanel', () => {
  afterEach(() => {
    singleInstanceDiagram.reset();
    currentInstance.reset();
    fetchWorkflowInstance.mockReset();
    fetchWorkflowInstanceIncidents.mockReset();
  });
  beforeAll(() => {
    fetchSequenceFlows.mockResolvedValue(mockSequenceFlows);
    fetchEvents.mockResolvedValue(mockEvents);
  });
  afterAll(() => {
    fetchSequenceFlows.mockReset();
    fetchEvents.mockReset();
  });

  it('should render spinner while loading', async () => {
    fetchWorkflowInstance.mockResolvedValueOnce({
      id: 'instance_id',
      state: 'ACTIVE',
    });

    render(<TopPanel />, {wrapper: Wrapper});

    currentInstance.init(1);
    singleInstanceDiagram.fetchWorkflowXml(1);
    expect(screen.getByTestId('spinner')).toBeInTheDocument();
    await waitForElementToBeRemoved(screen.getByTestId('spinner'));
  });

  it('should render incident bar', async () => {
    fetchWorkflowInstance.mockResolvedValueOnce({
      id: 'instance_id',
      state: 'INCIDENT',
    });

    fetchWorkflowInstanceIncidents.mockResolvedValueOnce(mockIncidents);
    render(<TopPanel />, {wrapper: Wrapper});

    currentInstance.init(1);
    await singleInstanceDiagram.fetchWorkflowXml(1);
    expect(
      await screen.findByText('There is 1 Incident in Instance 1.')
    ).toBeInTheDocument();
  });
});
