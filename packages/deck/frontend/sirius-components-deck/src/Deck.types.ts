/*******************************************************************************
 * Copyright (c) 2023, 2024 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Obeo - initial API and implementation
 *******************************************************************************/

import { SelectionEntry } from '@eclipse-sirius/sirius-components-core';

export interface DeckProps {
  data: DeckData;
  onCardClick: (cardId: string, metadata: CardMetadata, laneId: string) => void;
}
export interface OnCardClickProps {
  cardId: String;
  metadata: any;
  laneId: String;
}
export interface DeckData {
  lanes: Lane[];
}

export interface Lane {
  id: string;
  title: string;
  label: string;
  cards: Card[];
}
export interface Card {
  id: string;
  title: string;
  label: string;
  description: string;
  metadata?: CardMetadata;
  className?: string;
  editable?: boolean;
}

export interface CardMetadata {
  selection: SelectionEntry;
}