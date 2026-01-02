import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './app/App';
import './styles/theme.css';
import { runStatusAggregationSelfCheck } from './domain/statusAggregation';

if (import.meta.env.DEV) {
  (window as any).__o2cStatusSelfCheck = runStatusAggregationSelfCheck;
}

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
