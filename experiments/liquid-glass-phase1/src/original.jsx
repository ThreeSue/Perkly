// The upstream example and styles below are unmodified. MIT: see ../upstream/LICENSE.
import React from 'react';
import { createRoot } from 'react-dom/client';
import App from '../upstream/examples/src/App.tsx';
import '../upstream/examples/src/index.css';
import '../upstream/examples/src/App.css';
createRoot(document.getElementById('root')).render(<App />);
