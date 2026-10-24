import { lazy, Suspense } from "react";
import { BrowserRouter, Route, Routes } from "react-router-dom";
import { QueryClientProvider } from "@tanstack/react-query";
import { Toaster } from "react-hot-toast";
import AppLayout from "./ui/AppLayout";
import ProtectedRoute from "./features/Auth/ProtectedRoute";
import AdminRoute from "./features/Auth/AdminRoute";
import RouteLoading from "./ui/RouteLoading";
import { queryClient } from "./utils/queryClient";

import Login from "./pages/Login";

const Dashboard = lazy(() => import("./pages/Dashboard"));
const Explore = lazy(() => import("./pages/Explore/Explore"));
const ManageItems = lazy(() => import("./pages/ManageItems"));
const ManageCategory = lazy(() => import("./pages/ManageCategory"));
const ManageModifiers = lazy(() => import("./pages/ManageModifiers"));
const ManageUsers = lazy(() => import("./pages/ManageUsers"));
const ManagePromotions = lazy(() => import("./pages/ManagePromotions"));
const ManageCustomers = lazy(() => import("./pages/ManageCustomers"));
const OrderHistory = lazy(() => import("./pages/OrderHistory"));
const ActivityLogs = lazy(() => import("./pages/ActivityLogs"));
const NotFound = lazy(() => import("./ui/NotFound"));

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Toaster />

        <Suspense fallback={<RouteLoading />}>
          <Routes>
            <Route path="/login" element={<Login />} />
            {/* <Route path="/register" element={<Register />} />  */}
            <Route element={<ProtectedRoute />}>
              <Route element={<AppLayout />}>
                <Route path="/dashboard" element={<Dashboard />} />
                <Route path="/explore" element={<Explore />} />
                <Route element={<AdminRoute />}>
                  <Route path="/items" element={<ManageItems />} />
                  <Route path="/categories" element={<ManageCategory />} />
                  <Route path="/modifiers" element={<ManageModifiers />} />
                  <Route path="/users" element={<ManageUsers />} />
                  <Route path="/promotions" element={<ManagePromotions />} />
                  <Route path="/customers" element={<ManageCustomers />} />
                </Route>
                <Route path="/orders" element={<OrderHistory />} />
                <Route path="/activity-logs" element={<ActivityLogs />} />
                <Route path="/" element={<Dashboard />} />
              </Route>
            </Route>
            <Route path="*" element={<NotFound />} />
          </Routes>
        </Suspense>
      </BrowserRouter>
    </QueryClientProvider>
  );
}
export default App;
